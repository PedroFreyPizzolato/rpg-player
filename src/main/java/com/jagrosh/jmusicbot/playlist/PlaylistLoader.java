/*
 * Copyright 2018 John Grosh (jagrosh).
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
package com.jagrosh.jmusicbot.playlist;

import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class PlaylistLoader
{
    private static final Logger LOG = LoggerFactory.getLogger(PlaylistLoader.class);

    public enum PlaylistErrorType
    {
        INVALID_CONFIG,
        STORAGE_UNAVAILABLE,
        PLAYLIST_NOT_FOUND
    }

    public static final class PlaylistError
    {
        private final PlaylistErrorType type;
        private final String message;
        private final String configuredPath;
        private final Throwable cause;

        private PlaylistError(PlaylistErrorType type, String message, String configuredPath, Throwable cause)
        {
            this.type = type;
            this.message = message;
            this.configuredPath = configuredPath;
            this.cause = cause;
        }

        public static PlaylistError of(PlaylistErrorType type, String message, String configuredPath, Throwable cause)
        {
            return new PlaylistError(type, message, configuredPath, cause);
        }

        public PlaylistErrorType getType()
        {
            return type;
        }

        public String getMessage()
        {
            return message;
        }

        public String getConfiguredPath()
        {
            return configuredPath;
        }

        public Throwable getCause()
        {
            return cause;
        }
    }

    public static final class PlaylistResult<T>
    {
        private final T value;
        private final PlaylistError error;

        private PlaylistResult(T value, PlaylistError error)
        {
            this.value = value;
            this.error = error;
        }

        public static <T> PlaylistResult<T> success(T value)
        {
            return new PlaylistResult<>(value, null);
        }

        public static <T> PlaylistResult<T> failure(PlaylistError error)
        {
            return new PlaylistResult<>(null, error);
        }

        public boolean isSuccess()
        {
            return error == null;
        }

        public T getValue()
        {
            return value;
        }

        public PlaylistError getError()
        {
            return error;
        }
    }

    private final BotConfig config;
    private volatile PlaylistError lastStorageError;
    
    public PlaylistLoader(BotConfig config)
    {
        this.config = config;
    }
    
    public PlaylistResult<Path> ensureStorageReady()
    {
        return ensureStorageReady(true);
    }

    public PlaylistResult<Path> checkStorageReady()
    {
        return ensureStorageReady(false);
    }

    private PlaylistResult<Path> ensureStorageReady(boolean createIfMissing)
    {
        String configuredPath = config.getPlaylistsFolder();
        if(configuredPath == null || configuredPath.trim().isEmpty())
        {
            return rememberError(PlaylistErrorType.INVALID_CONFIG, "Playlists folder is not configured.", configuredPath, null);
        }

        Path folderPath;
        try
        {
            folderPath = OtherUtil.getPath(configuredPath).toAbsolutePath().normalize();
        }
        catch(Exception ex)
        {
            return rememberError(PlaylistErrorType.INVALID_CONFIG,
                    "Playlists folder path is invalid: " + ex.getMessage(), configuredPath, ex);
        }

        try
        {
            if(createIfMissing)
            {
                Files.createDirectories(folderPath);
            }
            else if(!Files.exists(folderPath))
            {
                return rememberError(PlaylistErrorType.STORAGE_UNAVAILABLE,
                        "Playlists directory does not exist and could not be accessed.", configuredPath, null);
            }

            if(!Files.isDirectory(folderPath))
            {
                return rememberError(PlaylistErrorType.INVALID_CONFIG,
                        "Configured playlists path is not a directory.", configuredPath, null);
            }
            if(!Files.isReadable(folderPath) || !Files.isWritable(folderPath))
            {
                return rememberError(PlaylistErrorType.STORAGE_UNAVAILABLE,
                        "Playlists directory is not readable and writable.", configuredPath, null);
            }

            lastStorageError = null;
            return PlaylistResult.success(folderPath);
        }
        catch(IOException ex)
        {
            return rememberError(PlaylistErrorType.STORAGE_UNAVAILABLE,
                    "Failed to access playlists directory: " + ex.getMessage(), configuredPath, ex);
        }
    }

    public PlaylistResult<List<String>> getPlaylistNamesResult()
    {
        PlaylistResult<Path> readiness = ensureStorageReady();
        if(!readiness.isSuccess())
            return PlaylistResult.failure(readiness.getError());

        File folder = readiness.getValue().toFile();
        File[] files = folder.listFiles((pathname) -> pathname.getName().endsWith(".txt"));
        if(files == null)
        {
            return rememberError(PlaylistErrorType.STORAGE_UNAVAILABLE,
                    "Failed to list playlists directory contents.", config.getPlaylistsFolder(), null);
        }

        List<String> names = Arrays.stream(files)
                .map(f -> f.getName().substring(0, f.getName().length() - 4))
                .collect(Collectors.toList());
        return PlaylistResult.success(names);
    }

    public List<String> getPlaylistNames()
    {
        PlaylistResult<List<String>> result = getPlaylistNamesResult();
        return result.isSuccess() ? result.getValue() : null;
    }

    public void createFolder()
    {
        ensureStorageReady();
    }
    
    public boolean folderExists()
    {
        String path = config.getPlaylistsFolder();
        if(path == null || path.trim().isEmpty())
            return false;
        try
        {
            return Files.isDirectory(OtherUtil.getPath(path));
        }
        catch(Exception ex)
        {
            return false;
        }
    }
    
    public void createPlaylist(String name) throws IOException
    {
        PlaylistResult<Void> result = createPlaylistResult(name);
        if(!result.isSuccess())
            throw toIOException(result.getError());
    }
    
    public void deletePlaylist(String name) throws IOException
    {
        PlaylistResult<Void> result = deletePlaylistResult(name);
        if(!result.isSuccess())
            throw toIOException(result.getError());
    }
    
    public void writePlaylist(String name, String text) throws IOException
    {
        PlaylistResult<Void> result = writePlaylistResult(name, text);
        if(!result.isSuccess())
            throw toIOException(result.getError());
    }

    public PlaylistResult<Void> createPlaylistResult(String name)
    {
        PlaylistResult<Path> readiness = ensureStorageReady();
        if(!readiness.isSuccess())
            return PlaylistResult.failure(readiness.getError());
        try
        {
            Files.createFile(playlistPath(readiness.getValue(), name));
            return PlaylistResult.success(null);
        }
        catch(IOException ex)
        {
            return rememberError(PlaylistErrorType.STORAGE_UNAVAILABLE,
                    "Failed to create playlist `" + name + "`: " + ex.getMessage(), config.getPlaylistsFolder(), ex);
        }
    }

    public PlaylistResult<Void> deletePlaylistResult(String name)
    {
        PlaylistResult<Path> readiness = ensureStorageReady();
        if(!readiness.isSuccess())
            return PlaylistResult.failure(readiness.getError());
        Path path = playlistPath(readiness.getValue(), name);
        if(!Files.exists(path))
        {
            return PlaylistResult.failure(new PlaylistError(PlaylistErrorType.PLAYLIST_NOT_FOUND,
                    "Playlist `" + name + "` does not exist.", config.getPlaylistsFolder(), null));
        }
        try
        {
            Files.delete(path);
            return PlaylistResult.success(null);
        }
        catch(IOException ex)
        {
            return rememberError(PlaylistErrorType.STORAGE_UNAVAILABLE,
                    "Failed to delete playlist `" + name + "`: " + ex.getMessage(), config.getPlaylistsFolder(), ex);
        }
    }

    public PlaylistResult<Void> writePlaylistResult(String name, String text)
    {
        PlaylistResult<Path> readiness = ensureStorageReady();
        if(!readiness.isSuccess())
            return PlaylistResult.failure(readiness.getError());

        Path path = playlistPath(readiness.getValue(), name);
        if(!Files.exists(path))
        {
            return PlaylistResult.failure(new PlaylistError(PlaylistErrorType.PLAYLIST_NOT_FOUND,
                    "Playlist `" + name + "` does not exist.", config.getPlaylistsFolder(), null));
        }
        try
        {
            Files.write(path, text.trim().getBytes());
            return PlaylistResult.success(null);
        }
        catch(IOException ex)
        {
            return rememberError(PlaylistErrorType.STORAGE_UNAVAILABLE,
                    "Failed to write playlist `" + name + "`: " + ex.getMessage(), config.getPlaylistsFolder(), ex);
        }
    }
    
    public Playlist getPlaylist(String name)
    {
        PlaylistResult<Playlist> result = getPlaylistResult(name);
        return result.isSuccess() ? result.getValue() : null;
    }

    public PlaylistResult<Playlist> getPlaylistResult(String name)
    {
        PlaylistResult<Path> readiness = ensureStorageReady();
        if(!readiness.isSuccess())
            return PlaylistResult.failure(readiness.getError());
        Path path = playlistPath(readiness.getValue(), name);
        if(!Files.exists(path))
        {
            return PlaylistResult.failure(new PlaylistError(PlaylistErrorType.PLAYLIST_NOT_FOUND,
                    "Playlist `" + name + "` does not exist.", config.getPlaylistsFolder(), null));
        }
        try
        {
            boolean[] shuffle = {false};
            List<String> list = new ArrayList<>();
            Files.readAllLines(path).forEach(str ->
            {
                String s = str.trim();
                if(s.isEmpty())
                    return;
                if(s.startsWith("#") || s.startsWith("//"))
                {
                    s = s.replaceAll("\\s+", "");
                    if(s.equalsIgnoreCase("#shuffle") || s.equalsIgnoreCase("//shuffle"))
                        shuffle[0]=true;
                }
                else
                    list.add(s);
            });
            if(shuffle[0])
                shuffle(list);
            return PlaylistResult.success(new Playlist(name, list, shuffle[0]));
        }
        catch(IOException e)
        {
            return rememberError(PlaylistErrorType.STORAGE_UNAVAILABLE,
                    "Failed to read playlist `" + name + "`: " + e.getMessage(), config.getPlaylistsFolder(), e);
        }
    }

    public Optional<PlaylistError> getLastStorageError()
    {
        return Optional.ofNullable(lastStorageError);
    }

    private <T> PlaylistResult<T> rememberError(PlaylistErrorType type, String message, String configuredPath, Throwable cause)
    {
        PlaylistError error = PlaylistError.of(type, message, configuredPath, cause);
        if(type != PlaylistErrorType.PLAYLIST_NOT_FOUND)
        {
            lastStorageError = error;
            if(cause == null)
                LOG.warn("Playlist storage issue ({}): path={}, message={}", type, configuredPath, message);
            else
                LOG.warn("Playlist storage issue ({}): path={}, message={}, cause={}",
                        type, configuredPath, message, cause.getMessage());
        }
        return PlaylistResult.failure(error);
    }

    private Path playlistPath(Path folder, String name)
    {
        return folder.resolve(name + ".txt");
    }

    private IOException toIOException(PlaylistError error)
    {
        if(error.getCause() instanceof IOException ioEx)
            return ioEx;
        return new IOException(error.getMessage(), error.getCause());
    }
    
    
    private static <T> void shuffle(List<T> list)
    {
        for(int first =0; first<list.size(); first++)
        {
            int second = (int)(Math.random()*list.size());
            T tmp = list.get(first);
            list.set(first, list.get(second));
            list.set(second, tmp);
        }
    }
    
    
    public class Playlist
    {
        private final String name;
        private final List<String> items;
        private final boolean shuffle;
        private final List<AudioTrack> tracks = new LinkedList<>();
        private final List<PlaylistLoadError> errors = new LinkedList<>();
        private boolean loaded = false;
        
        private Playlist(String name, List<String> items, boolean shuffle)
        {
            this.name = name;
            this.items = items;
            this.shuffle = shuffle;
        }
        
        public void loadTracks(AudioPlayerManager manager, Consumer<AudioTrack> consumer, Runnable callback)
        {
            if(loaded)
                return;
            loaded = true;
            if(items.isEmpty())
            {
                if(callback != null)
                    callback.run();
                return;
            }
            AtomicInteger pendingItems = new AtomicInteger(items.size());
            for(int i=0; i<items.size(); i++)
            {
                int index = i;
                manager.loadItemOrdered(name, items.get(i), new AudioLoadResultHandler() 
                {
                    private void done()
                    {
                        if(pendingItems.decrementAndGet() == 0)
                        {
                            if(shuffle)
                                shuffleTracks();
                            if(callback != null)
                                callback.run();
                        }
                    }

                    @Override
                    public void trackLoaded(AudioTrack at) 
                    {
                        if(config.isTooLong(at))
                            errors.add(new PlaylistLoadError(index, items.get(index), "This track is longer than the allowed maximum"));
                        else
                        {
                            at.setUserData(0L);
                            tracks.add(at);
                            consumer.accept(at);
                        }
                        done();
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist ap) 
                    {
                        if(ap.isSearchResult())
                        {
                            trackLoaded(ap.getTracks().get(0));
                        }
                        else if(ap.getSelectedTrack()!=null)
                        {
                            trackLoaded(ap.getSelectedTrack());
                        }
                        else
                        {
                            List<AudioTrack> loaded = new ArrayList<>(ap.getTracks());
                            if(shuffle)
                                for(int first =0; first<loaded.size(); first++)
                                {
                                    int second = (int)(Math.random()*loaded.size());
                                    AudioTrack tmp = loaded.get(first);
                                    loaded.set(first, loaded.get(second));
                                    loaded.set(second, tmp);
                                }
                            loaded.removeIf(track -> config.isTooLong(track));
                            loaded.forEach(at -> at.setUserData(0L));
                            tracks.addAll(loaded);
                            loaded.forEach(at -> consumer.accept(at));
                        }
                        done();
                    }

                    @Override
                    public void noMatches() 
                    {
                        errors.add(new PlaylistLoadError(index, items.get(index), "No matches found."));
                        done();
                    }

                    @Override
                    public void loadFailed(FriendlyException fe) 
                    {
                        errors.add(new PlaylistLoadError(index, items.get(index), "Failed to load track: "+fe.getLocalizedMessage()));
                        done();
                    }
                });
            }
        }
        
        public void shuffleTracks()
        {
            shuffle(tracks);
        }
        
        public String getName()
        {
            return name;
        }

        public List<String> getItems()
        {
            return items;
        }

        public List<AudioTrack> getTracks()
        {
            return tracks;
        }
        
        public List<PlaylistLoadError> getErrors()
        {
            return errors;
        }
    }
    
    public class PlaylistLoadError
    {
        private final int number;
        private final String item;
        private final String reason;
        
        private PlaylistLoadError(int number, String item, String reason)
        {
            this.number = number;
            this.item = item;
            this.reason = reason;
        }
        
        public int getIndex()
        {
            return number;
        }
        
        public String getItem()
        {
            return item;
        }
        
        public String getReason()
        {
            return reason;
        }
    }
}
