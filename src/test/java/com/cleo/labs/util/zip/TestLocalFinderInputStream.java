package com.cleo.labs.util.zip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

import com.google.common.io.ByteStreams;
import com.google.gwt.thirdparty.guava.common.primitives.Ints;

public class TestLocalFinderInputStream {

    @Test
    public void test() throws IOException {
        Path root = Paths.get(".");
        root = Paths.get(System.getProperty("user.home"),"d/vagrant/cache/zip");
        //root = Paths.get(System.getProperty("user.home"),"d/vagrant/harmony/test");
        try (LocalFinderInputStream in = LocalFinderInputStream.builder(root.toFile())
                .filter(Finder.excluding("glob:.*", "glob:target"))
                .build()) {
            byte[] buf = new byte[Integer.BYTES];
            ByteStreams.readFully(in, buf);
            int length = Ints.fromByteArray(buf);
            long exhaust = ByteStreams.exhaust(in);
            assertEquals((long)length, exhaust);
        }
    }

    @Test
    public void testDecoder() {
        Path root = Paths.get(".");
        root = Paths.get(System.getProperty("user.home"),"d/vagrant/cache/zip");
        //root = Paths.get(System.getProperty("user.home"),"d/vagrant/harmony/test");
        try (LocalFinderInputStream in = LocalFinderInputStream.builder(root.toFile())
                .filter(Finder.excluding("glob:.*", "glob:target"))
                .build()) {
            RemoteFinderStreamDecoder decoder = new RemoteFinderStreamDecoder(in);
            int count = 0;
            for (Found found : decoder) {
                System.out.println(String.format("%s: %d contents", found.fullname(), found.contents().length));
                count++;
            }
            decoder.throwIfException();
            assertEquals(1, count);
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }

}
