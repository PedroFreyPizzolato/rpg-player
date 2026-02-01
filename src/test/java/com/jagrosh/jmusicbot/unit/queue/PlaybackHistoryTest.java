package com.jagrosh.jmusicbot.unit.queue;

import com.jagrosh.jmusicbot.queue.PlaybackHistory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class PlaybackHistoryTest {

    @Test
    public void testAddAndSize() {
        PlaybackHistory<String> history = new PlaybackHistory<>(3);
        
        history.add("one");
        assertEquals(1, history.size());
        assertEquals("one", history.get(0));

        history.add("two");
        assertEquals(2, history.size());
        assertEquals("two", history.get(0));
        assertEquals("one", history.get(1));
    }

    @Test
    public void testMaxSize() {
        PlaybackHistory<String> history = new PlaybackHistory<>(2);

        history.add("one");
        history.add("two");
        history.add("three");

        assertEquals(2, history.size());
        assertEquals("three", history.get(0));
        assertEquals("two", history.get(1));
        
        // Ensure "one" was removed (it was the oldest)
        List<String> list = history.getList();
        assertFalse(list.contains("one"));
    }

    @Test
    public void testRemoveFirst() {
        PlaybackHistory<String> history = new PlaybackHistory<>(10);
        history.add("one");
        history.add("two");

        assertEquals("two", history.removeFirst());
        assertEquals(1, history.size());
        assertEquals("one", history.get(0));

        assertEquals("one", history.removeFirst());
        assertTrue(history.isEmpty());
        assertNull(history.removeFirst());
    }

    @Test
    public void testSetMaxSizeShrink() {
        PlaybackHistory<String> history = new PlaybackHistory<>(5);
        history.add("1");
        history.add("2");
        history.add("3");
        history.add("4");
        history.add("5");

        history.setMaxSize(2);
        assertEquals(2, history.size());
        assertEquals("5", history.get(0));
        assertEquals("4", history.get(1));
    }

    @Test
    public void testSetNegativeMaxSize() {
        PlaybackHistory<String> history = new PlaybackHistory<>(10);
        assertThrows(IllegalArgumentException.class, () -> history.setMaxSize(-1));
    }

    @Test
    public void testSetZeroMaxSize() {
        assertThrows(IllegalArgumentException.class, () -> new PlaybackHistory<String>(0));
        
        PlaybackHistory<String> history = new PlaybackHistory<>(10);
        assertThrows(IllegalArgumentException.class, () -> history.setMaxSize(0));
    }

    @Test
    public void testClear() {
        PlaybackHistory<String> history = new PlaybackHistory<>(10);
        history.add("one");
        history.clear();
        assertTrue(history.isEmpty());
        assertEquals(0, history.size());
    }

    @Test
    public void testAddNullThrows() {
        PlaybackHistory<String> history = new PlaybackHistory<>(10);
        assertThrows(NullPointerException.class, () -> history.add(null));
    }

    @Test
    public void testConstructorRequiresPositiveSize() {
        assertThrows(IllegalArgumentException.class, () -> new PlaybackHistory<String>(0));
        assertThrows(IllegalArgumentException.class, () -> new PlaybackHistory<String>(-1));
    }
}
