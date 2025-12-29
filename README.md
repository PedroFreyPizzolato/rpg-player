<img align="right" src="https://i.imgur.com/zrE80HY.png" height="200" width="200">

# JMusicBot
> [!NOTE]
> This is a fork of [JMusicBot](https://github.com/jagrosh/MusicBot) from jagrosh
> I started this because it was not maintained and was not working anymore.

[![Downloads](https://img.shields.io/github/downloads/arif-banai/MusicBot/total.svg)](https://github.com/arif-banai/MusicBot/releases/latest)
[![Stars](https://img.shields.io/github/stars/arif-banai/MusicBot.svg)](https://github.com/arif-banai/MusicBot/stargazers)
[![Release](https://img.shields.io/github/release/arif-banai/MusicBot.svg)](https://github.com/arif-banai/MusicBot/releases/latest)
[![License](https://img.shields.io/github/license/arif-banai/MusicBot.svg)](https://github.com/arif-banai/MusicBot/blob/master/LICENSE)
[![Discord](https://discordapp.com/api/guilds/1453856673004392634/widget.png)](https://discord.gg/cyyUxNmmx6) <br>
[![CircleCI](https://dl.circleci.com/status-badge/img/gh/arif-banai/MusicBot/tree/master.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/arif-banai/MusicBot/tree/master)
[![Build and Test](https://github.com/arif-banai/MusicBot/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/arif-banai/MusicBot/actions/workflows/build-and-test.yml)
[![CodeFactor](https://www.codefactor.io/repository/github/arif-banai/musicbot/badge)](https://www.codefactor.io/repository/github/arif-banai/musicbot)

A cross-platform Discord music bot with a clean interface, and that is easy to set up and run yourself!

## ⚠️ Important Migration Notice (JDA 6 Update)

This version of JMusicBot includes a major infrastructure update to **JDA 6.2.0** and **Lavaplayer 2.2.6**. To ensure your bot continues to function correctly, please note the following mandatory changes:

*   **Java 17 Minimum:** The bot now requires **Java 17 or higher**. Please update your hosting environment (check `java -version`) before running the new JAR.
*   **Privileged Gateway Intents:** You **must** enable the **Message Content Intent** in your [Discord Developer Portal](https://discord.com/developers/applications).
    *   *Navigate to: Your Application > Bot > Privileged Gateway Intents > Toggle "Message Content Intent" to ON.*
    *   *Without this, the bot will not see your commands.*
*   **Audio Provider Update:** We have switched to the `dev.arbjerg` Lavaplayer fork and added the `dev.lavalink.youtube` source manager. This provides the most stable and up-to-date support for YouTube and other modern audio sources.
*   **Dependency Cleanup:** Support for niche/legacy sources provided by the old DuncteBot library (such as TikTok or Reddit audio) has been removed to reduce the project's footprint and improve stability. Standard sources (YouTube, SoundCloud, Bandcamp, Twitch, etc.) are unaffected.

[![Setup](http://i.imgur.com/VvXYp5j.png)](https://jmusicbot.com/setup)

## Features
  * Easy to run (just make sure Java is installed, and run!)
  * Fast loading of songs
  * No external keys needed (besides a Discord Bot token)
  * Smooth playback
  * Server-specific setup for the "DJ" role that can moderate the music
  * Clean and beautiful menus
  * Supports many sites, including Youtube, Soundcloud, and more
  * Supports many online radio/streams
  * Supports local files
  * Playlist support (both web/youtube, and local)

## Supported sources and formats
JMusicBot supports all sources and formats supported by [lavaplayer](https://github.com/sedmelluq/lavaplayer#supported-formats):
### Sources
  * YouTube
  * SoundCloud
  * Bandcamp
  * Vimeo
  * Twitch streams
  * Local files
  * HTTP URLs
### Formats
  * MP3
  * FLAC
  * WAV
  * Matroska/WebM (AAC, Opus or Vorbis codecs)
  * MP4/M4A (AAC codec)
  * OGG streams (Opus, Vorbis and FLAC codecs)
  * AAC streams
  * Stream playlists (M3U and PLS)

## Example
![Loading Example...](https://i.imgur.com/kVtTKvS.gif)

## Setup
Please see the [Setup Page](https://jmusicbot.com/setup) to run this bot yourself!

## Docker

JMusicBot can be run using Docker for easy deployment and management. Pre-built images are available from the GitHub Container Registry. The container is configured to run headless and automatically generate a default `config.txt` on first run.

### Quick Start

**No local build required!** The pre-built image is automatically pulled from the registry.

1. **Create a directory for your config:**
   ```bash
   mkdir -p /path/to/jmusicbot/config
   ```

2. **Use docker-compose** (recommended):
   ```bash
   # Copy the example compose file
   cp docker-compose.example.yml docker-compose.yml
   
   # Edit docker-compose.yml and update the volume path
   # Then start the container (image will be pulled automatically)
   docker-compose up -d
   ```

   Or use the example directly:
   ```yaml
   services:
     jmusicbot:
       image: ghcr.io/arif-banai/musicbot:latest  # or ghcr.io/arif-banai/musicbot:0.3.9 for a specific version
       container_name: jmusicbot
       environment:
         # Optional: additional JVM options (e.g., memory settings)
         # - JAVA_OPTS=-Xmx512m -Xms256m
       volumes:
         - /your/path/to/config:/config
       restart: unless-stopped
   ```

3. **First Run:**
   - On first run, if the mounted `/config` directory is empty, the bot will automatically generate a default `config.txt` file.
   - Edit `/your/path/to/config/config.txt` on your host and add your Discord bot token.
   - Restart the container: `docker-compose restart`

### Important Notes

- **Config Persistence:** The `/config` volume **must** be mounted for your configuration to persist. The bot reads and writes `config.txt` from `/config` (the container's working directory).
- **First Run:** If `config.txt` doesn't exist, the bot will generate a default one automatically. You'll need to edit it with your bot token before the bot can start.
- **Image Tags:** 
  - Use `ghcr.io/arif-banai/musicbot:latest` for the latest build from the default branch
  - Use `ghcr.io/arif-banai/musicbot:0.3.9` (replace with actual version) to pin a specific release version
  - **Recommendation:** For production, pin your image tag rather than using `latest`
- **JAVA_OPTS:** You can optionally set `JAVA_OPTS` environment variable to pass additional JVM arguments (e.g., `-Xmx512m -Xms256m` for memory settings).
- **No Ports Required:** The bot only makes outbound connections to Discord, so no ports need to be exposed.

### Building the Image Locally (Optional)

**Note:** Most users should use the pre-built images from the registry. Building locally is only needed if you:
- Want to customize the build
- Are developing/contributing to the project
- Need to build from a specific commit/branch

To build the Docker image locally:

```bash
docker build -t jmusicbot:local .
```

Then update your `docker-compose.yml` to use the local image:
```yaml
services:
  jmusicbot:
    image: jmusicbot:local  # instead of ghcr.io/arif-banai/musicbot
    # ... rest of config
```

The Dockerfile uses a multi-stage build:
- **Stage 1:** Builds the application with Maven (Java 17) - copies `pom.xml` first for better layer caching, then builds the shaded jar
- **Stage 2:** Creates a minimal runtime image with `eclipse-temurin:17-jre-slim` - copies the built jar as `/app/app.jar` and sets up the entrypoint script

The built jar is copied to `/app/app.jar` in the final image. The entrypoint script handles running the application with proper signal handling.

### Automated Image Publishing

Docker images are automatically built and published to GitHub Container Registry via GitHub Actions:

- **On push to `master` (default branch):** 
  - Tags as `ghcr.io/arif-banai/musicbot:latest`
  - Also tags with version from `pom.xml` if it's not a `-SNAPSHOT` version
- **On version tags (e.g., `v0.3.9`):** 
  - Tags as `ghcr.io/arif-banai/musicbot:0.3.9` (the "v" prefix is stripped from git tags)
  - Does not tag as `:latest` (only default branch gets `:latest`)
- **Multi-platform:** Images are built for both `linux/amd64` and `linux/arm64`
- **Version Extraction:** The workflow uses Python XML parsing to reliably extract the version from `pom.xml`, handling both direct version tags and parent-inherited versions

The workflow file is located at `.github/workflows/docker-build.yml`. No additional configuration is needed - the workflow uses `GITHUB_TOKEN` which is automatically provided by GitHub Actions and has the required permissions (`contents: read`, `packages: write`) for pushing to GHCR.

To view published images, visit: `https://github.com/arif-banai/MusicBot/pkgs/container/musicbot`

## Questions/Suggestions/Bug Reports
**Please read the [Issues List](https://github.com/arif-banai/MusicBot/issues) before suggesting a feature**. If you have a question, need troubleshooting help, or want to brainstorm a new feature, please start a [Discussion](https://github.com/arif-banai/MusicBot/discussions). If you'd like to suggest a feature or report a reproducible bug, please open an [Issue](https://github.com/arif-banai/MusicBot/issues) on this repository. If you like this bot, be sure to add a star to the libraries that make this possible: [**JDA**](https://github.com/DV8FromTheWorld/JDA) and [**lavaplayer**](https://github.com/lavalink-devs/lavaplayer)!

## Editing
This bot (and the source code here) might not be easy to edit for inexperienced programmers. The main purpose of having the source public is to show the capabilities of the libraries, to allow others to understand how the bot works, and to allow those knowledgeable about java, JDA, and Discord bot development to contribute. There are many requirements and dependencies required to edit and compile it, and there will not be support provided for people looking to make changes on their own. Instead, consider making a feature request (see the above section). If you choose to make edits, please do so in accordance with the Apache 2.0 License.
