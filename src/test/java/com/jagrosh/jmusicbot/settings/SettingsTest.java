package com.jagrosh.jmusicbot.settings;

import org.junit.Test;
import org.mockito.Mockito;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SettingsTest {

    @Test
    public void testSetVolume() {
        SettingsManager manager = mock(SettingsManager.class);
        Settings settings = new Settings(manager, 0, 0, 0, 100, null, RepeatMode.OFF, null, -1, QueueType.FAIR);
        
        settings.setVolume(50);
        assertEquals(50, settings.getVolume());
        verify(manager, times(1)).writeSettings();
    }

    @Test
    public void testSetRepeatMode() {
        SettingsManager manager = mock(SettingsManager.class);
        Settings settings = new Settings(manager, 0, 0, 0, 100, null, RepeatMode.OFF, null, -1, QueueType.FAIR);

        settings.setRepeatMode(RepeatMode.ALL);
        assertEquals(RepeatMode.ALL, settings.getRepeatMode());
        verify(manager, times(1)).writeSettings();
    }

    @Test
    public void testSetQueueType() {
        SettingsManager manager = mock(SettingsManager.class);
        Settings settings = new Settings(manager, 0, 0, 0, 100, null, RepeatMode.OFF, null, -1, QueueType.FAIR);

        settings.setQueueType(QueueType.LINEAR);
        assertEquals(QueueType.LINEAR, settings.getQueueType());
        verify(manager, times(1)).writeSettings();
    }

    @Test
    public void testSetPrefix() {
        SettingsManager manager = mock(SettingsManager.class);
        Settings settings = new Settings(manager, 0, 0, 0, 100, null, RepeatMode.OFF, null, -1, QueueType.FAIR);

        settings.setPrefix("!");
        assertEquals("!", settings.getPrefix());
        assertTrue(settings.getPrefixes().contains("!"));
        verify(manager, times(1)).writeSettings();
    }
}
