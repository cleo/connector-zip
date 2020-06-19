package com.cleo.labs.connector.zip;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import org.junit.Test;

public class TestFind {

    @Test
    public void test() throws IOException {
        Path root = Paths.get(".");
        Path abs = root.toAbsolutePath();
        System.out.println("absolute="+abs);
        Iterator<Path> files = Files.find(root, Integer.MAX_VALUE, (p, a) -> !a.isSymbolicLink()).iterator();
        while (files.hasNext()) {
            Path p = files.next();
            if (p.toFile().isDirectory()) {
                String name = root.relativize(p).toString();
                if (!name.isEmpty()) {
                    System.out.println(name+"/");
                }
            } else {
                System.out.println(root.relativize(p));
            }
        }
        assertTrue(true);
    }

    private int newLength(int n, int bufferSize) {
        return ((n + bufferSize-1) / bufferSize) * bufferSize;
    }

    @Test
    public void testNewLength() {
        assertEquals(8192, newLength(1, 8192));
        assertEquals(8192, newLength(8192, 8192));
        assertEquals(16384, newLength(8193, 8192));
        assertEquals(16384, newLength(16383, 8192));
        assertEquals(16384, newLength(16384, 8192));
        assertEquals(24576, newLength(16385, 8192));
    }

}
