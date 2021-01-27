package com.cleo.labs.util.zip;

import static org.junit.Assert.*;

import org.junit.Test;

public class TestPathUtil {

    @Test
    public void testAsDirectory() {
        assertEquals("a/", PathUtil.asDirectory("a"));
        assertEquals("/a/", PathUtil.asDirectory("/a"));
        assertEquals("/a\\", PathUtil.asDirectory("/a\\"));
        assertEquals("c:a/", PathUtil.asDirectory("c:a"));
        assertEquals("c:a\\", PathUtil.asDirectory("c:a\\"));
        assertEquals("c:a/", PathUtil.asDirectory("c:a/"));
        assertEquals("c:\\a/", PathUtil.asDirectory("c:\\a"));
        assertEquals("\\a\\b\\c/", PathUtil.asDirectory("\\a\\b\\c"));
        assertEquals("\\a\\b\\c\\", PathUtil.asDirectory("\\a\\b\\c\\"));
    }

    @Test
    public void testStripRoot() {
        assertEquals("a", PathUtil.stripRoot("a"));
        assertEquals("a", PathUtil.stripRoot("c:a"));
        assertEquals("a/b", PathUtil.stripRoot("c:a/b"));
        assertEquals("a/b", PathUtil.stripRoot("c:/a/b"));
        assertEquals("a", PathUtil.stripRoot("c:/a"));
        assertEquals("a", PathUtil.stripRoot("c:\\a"));
        assertEquals("a", PathUtil.stripRoot("c:\\\\a"));
        assertEquals("a", PathUtil.stripRoot("c:\\\\a"));
        assertEquals("a", PathUtil.stripRoot("/c:\\\\a"));
        assertEquals("a", PathUtil.stripRoot("/c:d:a"));
        assertEquals("a/b", PathUtil.stripRoot("/c:d:a/b"));
    }

    @Test
    public void testJustDirectory() {
        assertEquals("", PathUtil.justDirectory(""));
        assertEquals("/", PathUtil.justDirectory("/"));
        assertEquals("\\", PathUtil.justDirectory("\\"));
        assertEquals("", PathUtil.justDirectory("a"));
        assertEquals("", PathUtil.justDirectory("a/"));
        assertEquals("", PathUtil.justDirectory("a\\"));
        assertEquals("a/", PathUtil.justDirectory("a/b"));
        assertEquals("a\\", PathUtil.justDirectory("a\\b/"));
        assertEquals("a/", PathUtil.justDirectory("a/b\\"));
        assertEquals("c:", PathUtil.justDirectory("c:a"));
        assertEquals("c:a\\", PathUtil.justDirectory("c:a\\b"));
        assertEquals("\\\\host\\", PathUtil.justDirectory("\\\\host\\b/"));
    }

    @Test
    public void testJustFile() {
        assertEquals("", PathUtil.justFile(""));
        assertEquals("", PathUtil.justFile("/"));
        assertEquals("", PathUtil.justFile("\\"));
        assertEquals("", PathUtil.justFile("c:"));
        assertEquals("x", PathUtil.justFile("", "x"));
        assertEquals("x", PathUtil.justFile("/", "x"));
        assertEquals("x", PathUtil.justFile("\\", "x"));
        assertEquals("x", PathUtil.justFile("c:", "x"));
        assertEquals("a", PathUtil.justFile("a"));
        assertEquals("a", PathUtil.justFile("a/"));
        assertEquals("a", PathUtil.justFile("a\\"));
        assertEquals("d", PathUtil.justFile("a\\/b/c\\d/"));
        assertEquals("b", PathUtil.justFile("a/b"));
        assertEquals("b", PathUtil.justFile("a\\b/"));
        assertEquals("b", PathUtil.justFile("a/b\\"));
        assertEquals("a", PathUtil.justFile("c:a"));
        assertEquals("b", PathUtil.justFile("c:a\\b"));
        assertEquals("b", PathUtil.justFile("\\\\host\\b/"));
    }

    @Test
    public void testSafeChild() {
        assertArrayEquals(new String[] {"a"}, PathUtil.safePath(".././a/.."));
        assertArrayEquals(new String[] {"a"}, PathUtil.safePath(".././a/../"));
        assertArrayEquals(new String[] {"a"}, PathUtil.safePath("c:.././a/../"));
        assertArrayEquals(new String[] {"a"}, PathUtil.safePath("../\\\\./a////.././/\\\\/"));
        assertArrayEquals(new String[] {"a"}, PathUtil.safePath("..\\\\./a/../"));
        assertArrayEquals(new String[] {"a", "b"}, PathUtil.safePath("a/b"));
        assertArrayEquals(new String[] {"..a", "b"}, PathUtil.safePath("..a/b"));
        assertArrayEquals(new String[] {"a", "..b"}, PathUtil.safePath("a/..b"));
        assertArrayEquals(new String[] {"a", ".b"}, PathUtil.safePath("a/.b"));
        assertArrayEquals(new String[] {"a", "b."}, PathUtil.safePath("a/b."));
    }

}
