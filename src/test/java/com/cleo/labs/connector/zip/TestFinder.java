package com.cleo.labs.connector.zip;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.junit.Ignore;
import org.junit.Test;

import com.cleo.labs.util.zip.Finder;
import com.cleo.labs.util.zip.Finder.DirectoryMode;
import com.google.common.collect.Iterables;

public class TestFinder {

    @Ignore
    @Test
    public void test() throws IOException {
        Path root = Paths.get(".");
        root = Paths.get(System.getProperty("user.home"),"d/vagrant/cache/zip");
        Path abs = root.toAbsolutePath();
        int limit = 0;
        System.out.println("absolute="+abs);
        Finder files = new Finder(root.toFile())
                .filter(Finder.excluding("glob:.*", "glob:target"))
                .limit(limit)
                .directoryMode(DirectoryMode.excludeEmpty);
        //Iterator<Finder.Found> files = new Finder(root.toFile(), Finder.including("glob:**.java"), DirectoryMode.excludeEmpty);
        int count = 0;
        for (Finder.Found f : files) {
            count++;
            System.out.println(String.format("%d: %d.%d %s %s", count, f.depth, f.index, Arrays.toString(files.checkpoint()), f.fullname));
        }
        assertTrue(true);
    }

    @Ignore
    @Test
    public void testRestart() throws IOException {
        Path root = Paths.get(".");
        int[] restart = new int[] {2, 1, 0, 0, 0, 0, 1, 0, 2};
        int limit = 0;
        Finder files = new Finder(root.toFile())
                .filter(Finder.excluding("glob:.*", "glob:target"))
                .limit(limit)
                .restart(restart)
                .directoryMode(DirectoryMode.include);
        //Iterator<Finder.Found> files = new Finder(root.toFile(), Finder.including("glob:**.java"), DirectoryMode.excludeEmpty);
        int count = 0;
        while (files.hasNext()) {
            count++;
            Finder.Found f = files.next();
            System.out.println(String.format("%d: %d.%d %s %s", count, f.depth, f.index, Arrays.toString(files.checkpoint()), f.fullname));
        }
        assertTrue(true);
    }

    @Test
    public void testLimit() throws IOException {
        int limit = 17;
        Path root = Paths.get(".");
        Finder files = new Finder(root.toFile())
                .filter(Finder.excluding("glob:.*", "glob:target"))
                .directoryMode(DirectoryMode.excludeEmpty)
                .limit(limit);
        assertEquals(limit, Iterables.size(files));
    }

    @Test
    public void testFile() throws IOException {
        Finder files = new Finder(new File("LICENSE"));
        assertTrue(files.hasNext());
    }

    @Test
    @Ignore
    public void testIgnore() {
    }

}
