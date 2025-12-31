# Lavalink Integration Plan

## Overview
This document outlines the plan to integrate Lavalink support into JMusicBot as a configurable option, allowing users to choose between direct Lavaplayer (current) and Lavalink (new) audio processing.

## Architecture Goals
- **Backward Compatible**: Existing Lavaplayer mode remains default
- **Configurable**: Users can enable/disable Lavalink via config
- **Abstraction Layer**: Clean separation between audio providers
- **Feature Parity**: All existing features work with both modes

## Phase 1: Dependencies & Configuration

### 1.1 Add Lavalink Client Dependency
- **Library Options**:
  - **Option A (Recommended)**: Use Lavalink REST API + WebSocket directly
    - Add `com.squareup.okhttp3:okhttp` for REST API calls
    - Add `org.java-websocket:Java-WebSocket` for WebSocket connection
    - Implement Lavalink protocol ourselves (more control)
  - **Option B**: Use existing Java Lavalink client library (if available)
    - Search for `lavalink-client` on Maven Central
    - May need to check GitHub for community implementations
- **Action**: Add dependencies to `pom.xml`
- **Note**: Lavalink uses REST API for track loading and WebSocket for player control

### 1.2 Configuration Options
Add to `reference.conf`:
```hocon
// Enable Lavalink for audio processing
// If false, uses direct Lavaplayer (default)
uselavalink = false

// Lavalink server configuration
lavalink {
  // Server hostname or IP
  host = "localhost"
  
  // Server port (default: 2333)
  port = 2333
  
  // Server password
  password = "youshallnotpass"
  
  // Enable secure WebSocket (wss://)
  secure = false
  
  // Optional: Custom node identifier
  nodeid = "default"
  
  // Optional: User-provided node URL (for "bring-your-own-node")
  // If set, overrides host/port/secure
  customnodeurl = ""
}
```

### 1.3 Update BotConfig.java
- Add getter methods:
  - `useLavalink()`: Returns boolean
  - `getLavalinkHost()`: Returns String
  - `getLavalinkPort()`: Returns int
  - `getLavalinkPassword()`: Returns String
  - `getLavalinkSecure()`: Returns boolean
  - `getLavalinkNodeId()`: Returns String
  - `getLavalinkCustomNodeUrl()`: Returns String (nullable)

## Phase 2: Abstraction Layer

### 2.1 Create AudioProvider Interface
**File**: `src/main/java/com/jagrosh/jmusicbot/audio/AudioProvider.java`

```java
public interface AudioProvider {
    void init();
    AudioHandler createHandler(Guild guild);
    void loadItem(String identifier, AudioLoadResultHandler resultHandler);
    void shutdown();
    boolean isAvailable();
}
```

### 2.2 Create LavaplayerProvider
**File**: `src/main/java/com/jagrosh/jmusicbot/audio/LavaplayerProvider.java`
- Wraps existing `PlayerManager` functionality
- Implements `AudioProvider` interface
- Maintains current behavior

### 2.3 Create LavalinkProvider
**File**: `src/main/java/com/jagrosh/jmusicbot/audio/LavalinkProvider.java`
- Implements `AudioProvider` interface
- Manages Lavalink client connection
- Handles WebSocket/REST communication
- Manages Lavalink players per guild

## Phase 3: Lavalink Implementation

### 3.1 Lavalink Client Manager
**File**: `src/main/java/com/jagrosh/jmusicbot/audio/lavalink/LavalinkManager.java`
- Manages connection to Lavalink server
- Handles WebSocket connection lifecycle
- Manages node health/status
- Handles reconnection logic

### 3.2 Lavalink Audio Handler
**File**: `src/main/java/com/jagrosh/jmusicbot/audio/LavalinkAudioHandler.java`
- Extends or wraps `AudioHandler` interface
- Communicates with Lavalink player
- Handles track loading, playback control
- Manages queue operations

### 3.3 Update PlayerManager
- Make `PlayerManager` use `AudioProvider` abstraction
- Factory pattern to create appropriate provider
- Maintain backward compatibility

## Phase 4: Voice Connection Handling

### 4.1 Voice Connection Abstraction
- Lavalink handles voice connections differently
- Need to update voice connection logic:
  - **Lavaplayer**: Uses JDA's `AudioManager.openAudioConnection()`
  - **Lavalink**: Uses Lavalink's voice connection API

### 4.2 Update Connection Points
Files to update:
- `MusicCommand.java`: Voice connection logic
- `Listener.java`: Auto-connect on ready
- `Bot.java`: `closeAudioConnection()` method
- `AloneInVoiceHandler.java`: Voice state monitoring

## Phase 5: Feature Compatibility

### 5.1 Queue Management
- Ensure queue operations work with both providers
- `QueuedTrack` should work with both

### 5.2 Playback Controls
- Play, pause, stop, skip, volume
- Seeking (if supported by Lavalink)
- Repeat modes

### 5.3 Track Loading
- URL resolution
- Search functionality
- Playlist loading
- Local file support (may need special handling)

### 5.4 Event Handling
- Track start/end events
- Exception handling
- Player state updates

## Phase 6: Configuration & Validation

### 6.1 Startup Validation
- Check Lavalink server connectivity on startup
- Validate configuration
- Provide helpful error messages

### 6.2 Runtime Monitoring
- Monitor Lavalink connection health
- Handle disconnections gracefully
- Log connection status

## Phase 7: Testing & Documentation

### 7.1 Testing Checklist
- [ ] Direct Lavaplayer mode (default) still works
- [ ] Lavalink mode works with local server
- [ ] Lavalink mode works with remote server
- [ ] Custom node URL works
- [ ] All music commands work in both modes
- [ ] Queue operations work in both modes
- [ ] Voice connection/disconnection works
- [ ] Error handling works correctly
- [ ] Reconnection logic works

### 7.2 Documentation
- Update README with Lavalink setup instructions
- Add configuration examples
- Document "bring-your-own-node" feature
- Troubleshooting guide

## Implementation Order

1. **Phase 1**: Dependencies & Configuration (Foundation)
2. **Phase 2**: Abstraction Layer (Design)
3. **Phase 3**: Lavalink Implementation (Core)
4. **Phase 4**: Voice Connection Handling (Integration)
5. **Phase 5**: Feature Compatibility (Completeness)
6. **Phase 6**: Configuration & Validation (Robustness)
7. **Phase 7**: Testing & Documentation (Quality)

## Technical Considerations

### Lavalink Client Library
**Recommended Approach**: Implement Lavalink client using:
- **REST API**: Use `okhttp3` for HTTP requests to Lavalink REST endpoints
  - Track loading: `GET /loadtracks?identifier={query}`
  - Player updates: `PATCH /v4/sessions/{sessionId}/players/{guildId}`
  - Player info: `GET /v4/sessions/{sessionId}/players/{guildId}`
- **WebSocket**: Use `Java-WebSocket` for real-time communication
  - Connect to: `ws://host:port` or `wss://host:port` (if secure)
  - Send player updates, receive events
  - Handle voice state updates

**Alternative**: Look for existing Java Lavalink client libraries:
- Check Maven Central for `lavalink-client`
- Check GitHub for community implementations
- May need to adapt or contribute to existing projects

### Lavalink Protocol
Lavalink v4 uses:
- REST API v4 for track loading and player management
- WebSocket for real-time events and voice state updates
- JSON payloads for all communication

### Voice Connection
Lavalink uses a different voice connection model:
- Bot sends voice state updates to Lavalink
- Lavalink handles the actual Discord voice connection
- Need to update JDA voice connection logic

### Track Loading
- Lavalink has REST API for track loading
- May need to adapt track loading logic
- Ensure all source managers work (or document limitations)

### Error Handling
- Network failures
- Lavalink server downtime
- Invalid configurations
- Graceful degradation

## Files to Create/Modify

### New Files
1. `src/main/java/com/jagrosh/jmusicbot/audio/AudioProvider.java`
2. `src/main/java/com/jagrosh/jmusicbot/audio/LavaplayerProvider.java`
3. `src/main/java/com/jagrosh/jmusicbot/audio/LavalinkProvider.java`
4. `src/main/java/com/jagrosh/jmusicbot/audio/LavalinkAudioHandler.java`
5. `src/main/java/com/jagrosh/jmusicbot/audio/lavalink/LavalinkManager.java`
6. `src/main/java/com/jagrosh/jmusicbot/audio/lavalink/LavalinkNode.java`
7. `src/main/java/com/jagrosh/jmusicbot/audio/lavalink/LavalinkPlayer.java`

### Modified Files
1. `pom.xml` - Add Lavalink dependencies
2. `src/main/resources/reference.conf` - Add Lavalink config
3. `src/main/java/com/jagrosh/jmusicbot/BotConfig.java` - Add config getters
4. `src/main/java/com/jagrosh/jmusicbot/audio/PlayerManager.java` - Use abstraction
5. `src/main/java/com/jagrosh/jmusicbot/audio/AudioHandler.java` - May need updates
6. `src/main/java/com/jagrosh/jmusicbot/Bot.java` - Update initialization
7. `src/main/java/com/jagrosh/jmusicbot/commands/MusicCommand.java` - Voice connection
8. `src/main/java/com/jagrosh/jmusicbot/Listener.java` - Voice connection
9. `src/main/java/com/jagrosh/jmusicbot/audio/AloneInVoiceHandler.java` - Voice monitoring

## Success Criteria

1. ✅ Bot works with Lavaplayer (default, no config change)
2. ✅ Bot works with Lavalink when configured
3. ✅ All existing features work in both modes
4. ✅ Configuration is clear and well-documented
5. ✅ Error handling is robust
6. ✅ Performance is improved with Lavalink on cloud servers

## Future Enhancements

- Multiple Lavalink node support (load balancing)
- Automatic node selection based on load
- Node health monitoring dashboard
- Metrics/statistics collection
- Support for Lavalink plugins

## Quick Reference: Implementation Checklist

### Phase 1: Foundation
- [ ] Add `okhttp3` dependency to `pom.xml`
- [ ] Add `Java-WebSocket` dependency to `pom.xml`
- [ ] Add Lavalink config section to `reference.conf`
- [ ] Add config getters to `BotConfig.java`
- [ ] Test config loading

### Phase 2: Abstraction
- [ ] Create `AudioProvider` interface
- [ ] Create `LavaplayerProvider` (wraps existing code)
- [ ] Update `PlayerManager` to use provider abstraction
- [ ] Test that existing functionality still works

### Phase 3: Lavalink Core
- [ ] Create `LavalinkManager` for connection management
- [ ] Implement REST API client for track loading
- [ ] Implement WebSocket client for player control
- [ ] Create `LavalinkProvider` implementing `AudioProvider`
- [ ] Create `LavalinkAudioHandler` for guild-specific players
- [ ] Test basic connection and track loading

### Phase 4: Integration
- [ ] Update voice connection logic for Lavalink
- [ ] Update `MusicCommand` voice handling
- [ ] Update `Listener` auto-connect logic
- [ ] Update `Bot.closeAudioConnection()` method
- [ ] Test voice connections in both modes

### Phase 5: Features
- [ ] Implement queue operations for Lavalink
- [ ] Implement playback controls (play, pause, stop, skip)
- [ ] Implement volume control
- [ ] Implement seeking (if supported)
- [ ] Handle all events (track start, end, exception)
- [ ] Test all music commands

### Phase 6: Robustness
- [ ] Add connection validation on startup
- [ ] Implement reconnection logic
- [ ] Add error handling and logging
- [ ] Handle network failures gracefully
- [ ] Test error scenarios

### Phase 7: Polish
- [ ] Write comprehensive tests
- [ ] Update documentation
- [ ] Add configuration examples
- [ ] Create troubleshooting guide
- [ ] Performance testing

