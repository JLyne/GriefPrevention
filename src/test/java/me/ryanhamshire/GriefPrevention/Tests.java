package me.ryanhamshire.GriefPrevention;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Tests
{
    @Test
    public void testTrivial()
    {
        assertTrue(true);
    }

    @Test
    public void testWordFinderBeginningMiddleEnd()
    {
        WordFinder finder = new WordFinder(Arrays.asList("alpha", "beta", "gamma"));
        assertTrue(finder.hasMatch("alpha"));
        assertTrue(finder.hasMatch("alpha etc"));
        assertTrue(finder.hasMatch("etc alpha etc"));
        assertTrue(finder.hasMatch("etc alpha"));

        assertTrue(finder.hasMatch("beta"));
        assertTrue(finder.hasMatch("beta etc"));
        assertTrue(finder.hasMatch("etc beta etc"));
        assertTrue(finder.hasMatch("etc beta"));

        assertTrue(finder.hasMatch("gamma"));
        assertTrue(finder.hasMatch("gamma etc"));
        assertTrue(finder.hasMatch("etc gamma etc"));
        assertTrue(finder.hasMatch("etc gamma"));
    }

    @Test
    public void testWordFinderCasing()
    {
        WordFinder finder = new WordFinder(Collections.singletonList("aLPhA"));
        assertTrue(finder.hasMatch("alpha"));
        assertTrue(finder.hasMatch("aLPhA"));
        assertTrue(finder.hasMatch("AlpHa"));
        assertTrue(finder.hasMatch("ALPHA"));
    }

    @Test
    public void testWordFinderPunctuation()
    {
        WordFinder finder = new WordFinder(Collections.singletonList("alpha"));
        assertTrue(finder.hasMatch("What do you think,alpha?"));
    }

    @Test
    public void testWordFinderNoMatch()
    {
        WordFinder finder = new WordFinder(Collections.singletonList("alpha"));
        assertFalse(finder.hasMatch("Unit testing is smart."));
    }

    @Test
    public void testWordFinderEmptyList()
    {
        WordFinder finder = new WordFinder(Collections.emptyList());
        assertFalse(finder.hasMatch("alpha"));
        finder = new WordFinder(Collections.singletonList(""));
        assertFalse(finder.hasMatch("beta"));
    }

    @Test
    public void testWordFinderPunctuationOnly()
    {
        WordFinder finder = new WordFinder(Collections.singletonList("alpha"));
        assertFalse(finder.hasMatch("!"));
        assertFalse(finder.hasMatch("?"));
    }

    @Test
    public void testWordFinderStartingPunctuation()
    {
        WordFinder finder = new WordFinder(Collections.singletonList("alpha"));
        assertFalse(finder.hasMatch("!asas dfasdf"));
        assertFalse(finder.hasMatch("?asdfa sdfas df"));
    }

    private final UUID player1 = UUID.fromString("f13c5a98-3777-4659-a111-5617adb7d7fb");
    private final UUID player2 = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
}