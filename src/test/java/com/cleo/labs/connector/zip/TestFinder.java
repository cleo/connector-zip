package com.cleo.labs.connector.zip;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import org.junit.Test;

import com.cleo.labs.util.zip.Finder;
import com.google.common.base.Joiner;

public class TestFinder {

    @Test
    public void test() throws IOException {
        Path root = Paths.get(".");
        Path abs = root.toAbsolutePath();
        System.out.println("absolute="+abs);
        Iterator<Finder.Found> files = new Finder(root.toFile());
        while (files.hasNext()) {
            Finder.Found f = files.next();
            String name = Joiner.on('/').join(f.path);
            if (f.directory) {
                name += "/";
            }
            System.out.println(name);
        }
        assertTrue(true);
    }

    @Test
    public void testFile() throws IOException {
        Iterator<Finder.Found>files = new Finder(new File("LICENSE")); 
        assertFalse(files.hasNext());
    }

}
