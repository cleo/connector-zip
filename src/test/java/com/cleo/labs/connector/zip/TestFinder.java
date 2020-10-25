package com.cleo.labs.connector.zip;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import org.junit.Ignore;
import org.junit.Test;

import com.cleo.labs.util.zip.Finder;
import com.cleo.labs.util.zip.Finder.DirectoryMode;

public class TestFinder {

    @Ignore
    @Test
    public void test() throws IOException {
        Path root = Paths.get(".");
        Path abs = root.toAbsolutePath();
        System.out.println("absolute="+abs);
        //Iterator<Finder.Found> files = new Finder(root.toFile(), Finder.excluding("glob:.*", "glob:target"));
        Iterator<Finder.Found> files = new Finder(root.toFile(), Finder.including("glob:**.java"), DirectoryMode.excludeEmpty);
        while (files.hasNext()) {
            Finder.Found f = files.next();
            System.out.println(f.fullname);
        }
        assertTrue(true);
    }

    @Test
    public void testFile() throws IOException {
        Iterator<Finder.Found>files = new Finder(new File("LICENSE")); 
        assertTrue(files.hasNext());
    }

}
