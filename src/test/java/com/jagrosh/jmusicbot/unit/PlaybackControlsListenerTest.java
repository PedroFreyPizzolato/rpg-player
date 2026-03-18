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
package com.jagrosh.jmusicbot.unit;

import com.jagrosh.jmusicbot.listener.PlaybackControlsListener;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.testutil.listener.ListenerTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PlaybackControlsListener Tests")
public class PlaybackControlsListenerTest {

    private ListenerTestFixture fixture;
    private PlaybackControlsListener listener;

    @BeforeEach
    void setUp() {
        fixture = ListenerTestFixture.create();
        listener = new PlaybackControlsListener(fixture.getBot());
    }

    @Nested
    @DisplayName("onButtonInteraction")
    class OnButtonInteractionTests {
        @Test
        @DisplayName("ignores unknown button IDs")
        void onButtonInteraction_ignoresUnknownButtonId() {
            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            verify(fixture.getMusicService(), never()).stop(any(), any(), any());
            verify(fixture.getMusicService(), never()).pause(any(), any(), any());
            verify(fixture.getMusicService(), never()).skip(any(), any(), any());
        }

        @Test
        @DisplayName("handles stop button")
        void onButtonInteraction_handlesStopButton() {
            fixture.withButtonId("np_stop")
                    .withMemberInVoiceChannel()
                    .withAudioHandlerPlaying();

            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            verify(fixture.getMusicService()).stop(
                    eq(fixture.getGuild()),
                    eq(fixture.getMember()),
                    any(MusicService.OutputAdapter.class)
            );
        }

        @Test
        @DisplayName("handles pause button")
        void onButtonInteraction_handlesPauseButton() {
            fixture.withButtonId("np_pause")
                    .withMemberInVoiceChannel()
                    .withAudioHandlerPlaying();

            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            verify(fixture.getMusicService()).pause(
                    eq(fixture.getGuild()),
                    eq(fixture.getMember()),
                    any(MusicService.OutputAdapter.class)
            );
        }

        @Test
        @DisplayName("handles skip button")
        void onButtonInteraction_handlesSkipButton() {
            fixture.withButtonId("np_skip")
                    .withMemberInVoiceChannel()
                    .withAudioHandlerPlaying();

            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            verify(fixture.getMusicService()).skip(
                    eq(fixture.getGuild()),
                    eq(fixture.getMember()),
                    any(MusicService.OutputAdapter.class)
            );
        }

        @Test
        @DisplayName("handles previous button")
        void onButtonInteraction_handlesPreviousButton() {
            fixture.withButtonId("np_previous")
                    .withMemberInVoiceChannel()
                    .withAudioHandlerPlaying();

            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            verify(fixture.getMusicService()).previous(
                    eq(fixture.getGuild()),
                    eq(fixture.getMember()),
                    any(MusicService.OutputAdapter.class)
            );
        }

        @Test
        @DisplayName("handles shuffle button")
        void onButtonInteraction_handlesShuffleButton() {
            fixture.withButtonId("np_shuffle")
                    .withMemberInVoiceChannel()
                    .withAudioHandlerPlaying();

            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            verify(fixture.getMusicService()).shuffle(
                    eq(fixture.getGuild()),
                    eq(fixture.getMember()),
                    eq(0),
                    any(MusicService.OutputAdapter.class)
            );
        }

        @Test
        @DisplayName("handles repeat button")
        void onButtonInteraction_handlesRepeatButton() {
            fixture.withButtonId("np_repeat")
                    .withMemberInVoiceChannel()
                    .withAudioHandlerPlaying();

            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            verify(fixture.getMusicService()).cycleRepeatMode(
                    eq(fixture.getGuild()),
                    eq(fixture.getMember()),
                    any(MusicService.OutputAdapter.class)
            );
        }

        @Test
        @DisplayName("handles voldown button")
        void onButtonInteraction_handlesVoldownButton() {
            fixture.withButtonId("np_voldown")
                    .withMemberInVoiceChannel()
                    .withAudioHandlerPlaying();

            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            verify(fixture.getMusicService()).adjustVolume(
                    eq(fixture.getGuild()),
                    eq(fixture.getMember()),
                    eq(-10),
                    any(MusicService.OutputAdapter.class)
            );
        }

        @Test
        @DisplayName("handles volup button")
        void onButtonInteraction_handlesVolupButton() {
            fixture.withButtonId("np_volup")
                    .withMemberInVoiceChannel()
                    .withAudioHandlerPlaying();

            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            verify(fixture.getMusicService()).adjustVolume(
                    eq(fixture.getGuild()),
                    eq(fixture.getMember()),
                    eq(10),
                    any(MusicService.OutputAdapter.class)
            );
        }

        @Test
        @DisplayName("replies error when no audio handler")
        void onButtonInteraction_repliesErrorWhenNoHandler() {
            fixture.withButtonId("np_stop")
                    .withNoAudioHandler();

            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            verify(fixture.getButtonInteractionEvent()).reply("There is no music playing!");
            verify(fixture.getReplyAction()).setEphemeral(true);
        }

        @Test
        @DisplayName("replies error when user not in voice")
        void onButtonInteraction_repliesErrorWhenUserNotInVoice() {
            fixture.withButtonId("np_stop")
                    .withMemberNotInVoiceChannel()
                    .withAudioHandlerPlaying();

            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            verify(fixture.getButtonInteractionEvent()).reply("You must be in the same voice channel to use this!");
            verify(fixture.getReplyAction()).setEphemeral(true);
        }

        @Test
        @DisplayName("handles null guild gracefully")
        void onButtonInteraction_handlesNullGuild() {
            fixture.withButtonId("np_stop");
            when(fixture.getButtonInteractionEvent().getGuild()).thenReturn(null);

            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            verify(fixture.getMusicService(), never()).stop(any(), any(), any());
        }

        @Test
        @DisplayName("handles null member gracefully")
        void onButtonInteraction_handlesNullMember() {
            fixture.withButtonId("np_stop");
            when(fixture.getButtonInteractionEvent().getMember()).thenReturn(null);

            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            verify(fixture.getMusicService(), never()).stop(any(), any(), any());
        }
    }
}
