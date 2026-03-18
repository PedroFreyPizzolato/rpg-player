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

    @Test
    void parseSettingsButtonId_enumAction_returnsParsedValues()
    {
        var parsed = ComponentIdParsers.parseSettingsButtonId("settings_enum_layout_minimal_42");
        assertTrue(parsed.isPresent());
        assertEquals("enum", parsed.get().action());
        assertEquals("layout", parsed.get().key());
        assertEquals("minimal", parsed.get().value());
        assertEquals(42L, parsed.get().userId());
    }

    @Test
    void parseSettingsButtonId_openAction_returnsParsedValues()
    {
        var parsed = ComponentIdParsers.parseSettingsButtonId("settings_open_settc_987");
        assertTrue(parsed.isPresent());
        assertEquals("open", parsed.get().action());
        assertEquals("settc", parsed.get().key());
        assertEquals(987L, parsed.get().userId());
    }

    @Test
    void parseSettingsModalId_validId_returnsParsedValues()
    {
        var parsed = ComponentIdParsers.parseSettingsModalId("settings_modal_setskip_123");
        assertTrue(parsed.isPresent());
        assertEquals("setskip", parsed.get().key());
        assertEquals(123L, parsed.get().userId());
    }

    @Test
    void parseSettingsEntitySelectId_validId_returnsParsedValues()
    {
        var parsed = ComponentIdParsers.parseSettingsEntitySelectId("settings_entity_setdj_77");
        assertTrue(parsed.isPresent());
        assertEquals("setdj", parsed.get().key());
        assertEquals(null, parsed.get().originalPanelMessageId());
        assertEquals(77L, parsed.get().userId());
    }

    @Test
    void parseSettingsEntitySelectId_withPanelMessage_returnsParsedValues()
    {
        var parsed = ComponentIdParsers.parseSettingsEntitySelectId("settings_entity_setvc_5555_77");
        assertTrue(parsed.isPresent());
        assertEquals("setvc", parsed.get().key());
        assertEquals(5555L, parsed.get().originalPanelMessageId());
        assertEquals(77L, parsed.get().userId());
    }

    @Test
    void parseSettingsIds_invalidIds_returnEmpty()
    {
        assertTrue(ComponentIdParsers.parseSettingsButtonId("settings_modal_setvc_1").isEmpty());
        assertTrue(ComponentIdParsers.parseSettingsButtonId("settings_enum_layout_minimal_bad").isEmpty());
        assertTrue(ComponentIdParsers.parseSettingsModalId("settings_modal_setvc_bad").isEmpty());
        assertTrue(ComponentIdParsers.parseSettingsModalId("settings_open_setvc_1").isEmpty());
        assertTrue(ComponentIdParsers.parseSettingsEntitySelectId("settings_entity_setvc_bad").isEmpty());
        assertTrue(ComponentIdParsers.parseSettingsEntitySelectId("settings_modal_setvc_1").isEmpty());
        assertTrue(ComponentIdParsers.parseSettingsEntitySelectId("settings_entity_setvc_1_bad").isEmpty());
    }

    @Test
    void parseNowPlayingButtonId_validId_returnsParsedValues()
    {
        var parsed = ComponentIdParsers.parseNowPlayingButtonId("np_pause");
        assertTrue(parsed.isPresent());
        assertEquals("pause", parsed.get().action());
    }

    @Test
    void parseNowPlayingButtonId_invalidId_returnsEmpty()
    {
        assertTrue(ComponentIdParsers.parseNowPlayingButtonId("pause").isEmpty());
        assertTrue(ComponentIdParsers.parseNowPlayingButtonId("np_").isEmpty());
        assertTrue(ComponentIdParsers.parseNowPlayingButtonId("np_pause_more").isEmpty());
    }
}
