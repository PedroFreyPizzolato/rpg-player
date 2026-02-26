/*
 * Copyright 2026 Arif Banai (arif-banai)
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
package com.jagrosh.jmusicbot.unit.listener.interaction;

import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentIdParsersTest
{
    @Test
    void parsePlaylistsButtonId_validId_returnsParsedValues()
    {
        var parsed = ComponentIdParsers.parsePlaylistsButtonId("playlists_queue_2_13_123456789");
        assertTrue(parsed.isPresent());
        assertEquals("queue", parsed.get().action());
        assertEquals(2, parsed.get().page());
        assertEquals(13, parsed.get().selectedTrack());
        assertEquals(123456789L, parsed.get().userId());
    }

    @Test
    void parsePlaylistsButtonId_invalidId_returnsEmpty()
    {
        assertTrue(ComponentIdParsers.parsePlaylistsButtonId("playlists_bad").isEmpty());
        assertTrue(ComponentIdParsers.parsePlaylistsButtonId("history_queue_1_1_1").isEmpty());
    }
}
