/*
 * Copyright 2024
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.audio.lavalink;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jagrosh.jmusicbot.BotConfig;
import net.dv8tion.jda.api.JDA;
import okhttp3.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages connection to Lavalink server.
 * Handles WebSocket connection and REST API calls.
 * 
 * @author JMusicBot Contributors
 */
public class LavalinkManager
{
    private final static Logger LOGGER = LoggerFactory.getLogger(LavalinkManager.class);
    
    private final BotConfig config;
    private final JDA jda;
    private final OkHttpClient httpClient;
    private WebSocketClient webSocket;
    private String sessionId;
    private boolean connected = false;
    private String nodeUrl;
    
    // Event handler callback
    private java.util.function.Consumer<JsonObject> eventHandler;
    
    public LavalinkManager(BotConfig config, JDA jda)
    {
        this.config = config;
        this.jda = jda;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        
        // Determine node URL - Lavalink v4 requires /v4/websocket endpoint
        String customUrl = config.getLavalinkCustomNodeUrl();
        if(customUrl != null && !customUrl.isEmpty())
        {
            // If custom URL doesn't end with /v4/websocket, append it
            if(!customUrl.endsWith("/v4/websocket"))
            {
                this.nodeUrl = customUrl.endsWith("/") ? customUrl + "v4/websocket" : customUrl + "/v4/websocket";
            }
            else
            {
                this.nodeUrl = customUrl;
            }
        }
        else
        {
            String protocol = config.getLavalinkSecure() ? "wss" : "ws";
            this.nodeUrl = protocol + "://" + config.getLavalinkHost() + ":" + config.getLavalinkPort() + "/v4/websocket";
        }
    }
    
    /**
     * Connect to the Lavalink server.
     * 
     * @return CompletableFuture that completes when connected
     */
    private CompletableFuture<Void> connectionFuture;
    
    public CompletableFuture<Void> connect()
    {
        CompletableFuture<Void> future = new CompletableFuture<>();
        this.connectionFuture = future; // Store for ready event
        
        try
        {
            URI serverUri = new URI(nodeUrl);
            
            webSocket = new WebSocketClient(serverUri)
            {
                @Override
                public void onOpen(ServerHandshake handshake)
                {
                    LOGGER.info("Connected to Lavalink server: {}", nodeUrl);
                    connected = true;
                    // Don't complete future here - wait for ready event
                }
                
                @Override
                public void onMessage(String message)
                {
                    handleWebSocketMessage(message);
                }
                
                @Override
                public void onClose(int code, String reason, boolean remote)
                {
                    LOGGER.warn("Lavalink WebSocket closed: {} - {}", code, reason);
                    connected = false;
                    sessionId = null;
                    
                    // Attempt to reconnect after delay
                    if(remote)
                    {
                        reconnect();
                    }
                }
                
                @Override
                public void onError(Exception ex)
                {
                    LOGGER.error("Lavalink WebSocket error", ex);
                    connected = false;
                    if(connectionFuture != null && !connectionFuture.isDone())
                    {
                        connectionFuture.completeExceptionally(ex);
                    }
                }
            };
            
            // Add authorization header
            webSocket.addHeader("Authorization", config.getLavalinkPassword());
            webSocket.addHeader("User-Id", String.valueOf(jda.getSelfUser().getIdLong()));
            webSocket.addHeader("Client-Name", "JMusicBot");
            
            webSocket.connect();
        }
        catch(Exception e)
        {
            LOGGER.error("Failed to connect to Lavalink server", e);
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Handle incoming WebSocket messages.
     * 
     * @param message The JSON message from Lavalink
     */
    private void handleWebSocketMessage(String message)
    {
        try
        {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String op = json.get("op").getAsString();
            
            switch(op)
            {
                case "ready":
                    JsonObject data = json.getAsJsonObject("data");
                    sessionId = data.get("sessionId").getAsString();
                    LOGGER.info("Lavalink session ready: {}", sessionId);
                    // Complete the connection future now that we're ready
                    if(connectionFuture != null && !connectionFuture.isDone())
                    {
                        connectionFuture.complete(null);
                    }
                    break;
                    
                case "playerUpdate":
                    // Handle player state updates (position, etc.)
                    handlePlayerUpdate(json);
                    break;
                    
                case "event":
                    // Handle track events (TrackStartEvent, TrackEndEvent, etc.)
                    // Lavalink sends events with guildId at top level and event data in the message
                    JsonObject eventData = json.has("data") ? json.getAsJsonObject("data") : json;
                    // Ensure guildId is in the event data for handler
                    if(json.has("guildId") && !eventData.has("guildId"))
                    {
                        eventData.addProperty("guildId", json.get("guildId").getAsString());
                    }
                    handleEvent(eventData);
                    break;
                    
                default:
                    LOGGER.debug("Unhandled Lavalink op: {}", op);
            }
        }
        catch(Exception e)
        {
            LOGGER.error("Error handling Lavalink message", e);
        }
    }
    
    /**
     * Handle Lavalink events (TrackStartEvent, TrackEndEvent, etc.).
     * 
     * @param eventData The event data JSON object
     */
    private void handleEvent(JsonObject eventData)
    {
        String type = eventData.get("type").getAsString();
        LOGGER.debug("Lavalink event: {}", type);
        
        // Dispatch to event handler if set
        if(eventHandler != null)
        {
            eventHandler.accept(eventData);
        }
    }
    
    /**
     * Handle player update events from Lavalink.
     * 
     * @param json The player update JSON
     */
    private void handlePlayerUpdate(JsonObject json)
    {
        // Player updates contain position and timestamp
        // These can be used to sync track position
        if(eventHandler != null)
        {
            JsonObject eventData = new JsonObject();
            eventData.addProperty("type", "playerUpdate");
            eventData.add("data", json);
            eventHandler.accept(eventData);
        }
    }
    
    /**
     * Set the event handler callback.
     * 
     * @param handler The handler to receive events
     */
    public void setEventHandler(java.util.function.Consumer<JsonObject> handler)
    {
        this.eventHandler = handler;
    }
    
    /**
     * Send a WebSocket message to Lavalink.
     * 
     * @param message The JSON message to send
     */
    public void sendWebSocketMessage(JsonObject message)
    {
        if(webSocket != null && webSocket.isOpen())
        {
            webSocket.send(message.toString());
        }
        else
        {
            LOGGER.warn("Cannot send WebSocket message: not connected");
        }
    }
    
    /**
     * Load tracks from Lavalink REST API.
     * 
     * @param identifier The track identifier (URL, search query, etc.)
     * @return CompletableFuture with the JSON response
     */
    public CompletableFuture<JsonObject> loadTracks(String identifier)
    {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        
        try
        {
            String url = buildRestUrl("/loadtracks?identifier=" + java.net.URLEncoder.encode(identifier, "UTF-8"));
            
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", config.getLavalinkPassword())
                    .get()
                    .build();
            
            httpClient.newCall(request).enqueue(new Callback()
            {
                @Override
                public void onFailure(Call call, java.io.IOException e)
                {
                    LOGGER.error("Failed to load tracks from Lavalink", e);
                    future.completeExceptionally(e);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws java.io.IOException
                {
                    try(ResponseBody body = response.body())
                    {
                        if(!response.isSuccessful())
                        {
                            future.completeExceptionally(new RuntimeException("Lavalink REST API error: " + response.code()));
                            return;
                        }
                        
                        String jsonString = body.string();
                        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
                        future.complete(json);
                    }
                }
            });
        }
        catch(Exception e)
        {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Update a player on Lavalink.
     * 
     * @param guildId The guild ID
     * @param playerData The player update data
     */
    public void updatePlayer(long guildId, JsonObject playerData)
    {
        if(sessionId == null)
        {
            LOGGER.warn("Cannot update player: session not ready");
            return;
        }
        
        JsonObject message = new JsonObject();
        message.addProperty("op", "update");
        message.addProperty("guildId", String.valueOf(guildId));
        message.add("player", playerData);
        
        sendWebSocketMessage(message);
    }
    
    /**
     * Destroy a player on Lavalink.
     * 
     * @param guildId The guild ID
     */
    public void destroyPlayer(long guildId)
    {
        if(sessionId == null)
        {
            return;
        }
        
        JsonObject message = new JsonObject();
        message.addProperty("op", "destroy");
        message.addProperty("guildId", String.valueOf(guildId));
        
        sendWebSocketMessage(message);
    }
    
    /**
     * Build REST API URL.
     * 
     * @param path The API path
     * @return The full URL
     */
    private String buildRestUrl(String path)
    {
        // Convert WebSocket URL to HTTP URL
        // Remove /v4/websocket from the end if present
        String httpUrl = nodeUrl.replace("ws://", "http://").replace("wss://", "https://");
        if(httpUrl.endsWith("/v4/websocket"))
        {
            httpUrl = httpUrl.substring(0, httpUrl.length() - "/v4/websocket".length());
        }
        // Ensure path starts with /
        if(!path.startsWith("/"))
        {
            path = "/" + path;
        }
        return httpUrl + path;
    }
    
    /**
     * Attempt to reconnect to Lavalink.
     */
    private void reconnect()
    {
        LOGGER.info("Attempting to reconnect to Lavalink in 5 seconds...");
        new Thread(() -> {
            try
            {
                Thread.sleep(5000);
                connect();
            }
            catch(Exception e)
            {
                LOGGER.error("Reconnection failed", e);
            }
        }).start();
    }
    
    /**
     * Check if connected to Lavalink.
     * 
     * @return true if connected
     */
    public boolean isConnected()
    {
        return connected && webSocket != null && webSocket.isOpen();
    }
    
    /**
     * Get the session ID.
     * 
     * @return The session ID, or null if not connected
     */
    public String getSessionId()
    {
        return sessionId;
    }
    
    /**
     * Shutdown the connection.
     */
    public void shutdown()
    {
        if(webSocket != null)
        {
            webSocket.close();
        }
        httpClient.dispatcher().executorService().shutdown();
        connected = false;
        LOGGER.info("LavalinkManager shutdown");
    }
}

