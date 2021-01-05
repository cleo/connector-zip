package com.cleo.labs.util.zip;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;

import org.junit.Ignore;
import org.junit.Test;

import com.cleo.labs.util.zip.Finder.DirectoryMode;
import com.google.common.collect.Iterables;

public class TestFinder {

    private static MockBagOFiles bagOf100() {
        return  new MockBagOFiles()
                .files("test%d.txt", 1, 89, 1000, (byte)' ')
                .dirs("nines", 0, 1)
                    .files("test%d.txt", 90, 10, 1000, (byte)' ')
                    .up()
                .files("test%d.txt", 100, 1, 1000, (byte)' ')
                .dirs(".git", 0, 1)
                    .files("ignore%d", 1, 10000, 10, (byte)0)
                    .up()
                .dirs("target", 0, 1)
                    .files("ignore%d", 1, 10000, 10, (byte)0)
                    .up()
                .dirs("empty%d", 1, 2)
                    .up()
                .up();
    }

    @Test
    public void test() throws IOException {
        MockBagOFiles root = bagOf100();
        int limit = 0;
        Finder files = new Finder(root.root())
                .filter(Finder.excluding("glob:.*", "glob:target"))
                .limit(limit)
                .directoryMode(DirectoryMode.excludeEmpty);
        assertEquals(102, Iterables.size(files)); // root + nines (2 dirs) + test[1-100].txt + skipped .git and empty[1-2]
    }

    @Test
    public void testLimit() throws IOException {
        MockBagOFiles root = bagOf100();
        int limit = 17;
        Finder files = new Finder(root.root())
                .filter(Finder.excluding("glob:.*", "glob:target"))
                .limit(limit)
                .directoryMode(DirectoryMode.excludeEmpty);
        assertEquals(limit, Iterables.size(files)); // root + nines (2 dirs) + test[1-100].txt + skipped .git and empty[1-2]
    }

    @Test
    public void testRestart() throws IOException {
        MockBagOFiles root = bagOf100();
        int limit = 17;
        Finder files = new Finder(root.root())
                .filter(Finder.excluding("glob:.*", "glob:target"))
                .directoryMode(DirectoryMode.excludeEmpty);
        for (int i=0; i<limit; i++) {
            files.next();
        }
        files.hold();
        Finder restart = new Finder(root.root())
                .restart(files.checkpoint())
                .filter(Finder.excluding("glob:.*", "glob:target"))
                .directoryMode(DirectoryMode.excludeEmpty);
        assertEquals(1+102-limit, Iterables.size(restart)); // 1+ for the extra /
    }

    @Test
    public void testDirectories() throws IOException {
        MockBagOFiles root = bagOf100();
        int limit = 0;
        Finder files = new Finder(root.root())
                .filter(Finder.excluding("glob:.*", "glob:target"))
                .limit(limit)
                .directoryMode(DirectoryMode.only);
        /*
        int count = 0;
        for (Found f : files) {
            count++;
            System.out.println(String.format("%d: %s %s", count, f, Arrays.toString(f.contents())));
        }
        */
        Found[] found = Iterables.toArray(files, Found.class);
        Integer[] sizes = Stream.of(found).map(f->f.contents().length).toArray(Integer[]::new);
        assertEquals(1+1+2, found.length); // root + nines + empty[12]
        assertArrayEquals(new Integer[] {93, 0, 0, 10}, sizes); // / empty[12] nines
    }

    @Test
    public void testFile() throws IOException {
        File root = new MockBagOFiles().files("TEST", 0, 1, 100, (byte)' ').root().listFiles()[0];
        assertTrue(root.isFile());
        assertEquals("TEST", root.getName());
        Finder files = new Finder(root);
        assertTrue(files.hasNext());
    }

    @Ignore
    @Test
    public void testIgnore() {
    }

}
