package com.cleo.labs.util.zip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.FileInputStream;
import java.io.IOException;

import org.junit.Test;

import com.google.common.io.ByteStreams;
import com.google.gwt.thirdparty.guava.common.primitives.Ints;

public class TestLocalFinderInputStream {

    @Test
    public void test() throws IOException {
        MockBagOFiles root = new MockBagOFiles()
                .files("f%d.txt", 1, 50000, 10000, (byte)' ');
        try (LocalFinderInputStream in = LocalFinderInputStream.builder(root.root())
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
        MockBagOFiles root = new MockBagOFiles()
                .files("f%d.txt", 1, 50000, 10000, (byte)' ');
        try (LocalFinderInputStream in = LocalFinderInputStream.builder(root.root())
                .build();
             RemoteFinderStreamDecoder decoder = new RemoteFinderStreamDecoder(in)) {
            int dirs = 0;
            int contents = 0;
            for (Found found : decoder) {
                //System.out.println(String.format("%s: %d contents", found.fullname(), found.contents().length));
                dirs++;
                contents += found.contents().length;
            }
            decoder.throwIfException();
            assertEquals(1, dirs);
            assertEquals(50000, contents);
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testMockDecoder() {
        MockBagOFiles root = new MockBagOFiles()
                .files("t%d", 1, 2, 100, (byte)'-')
                .dirs("d%d", 1, 3)
                .dirs("e%d", 1, 3)
                .files("f%d.txt", 1, 10, 10000, (byte)' ')
                .up()
                .files("e%d.txt", 1, 10, 100, (byte)'.');
        //root = Paths.get(System.getProperty("user.home"),"d/vagrant/harmony/test");
        try (LocalFinderInputStream in = LocalFinderInputStream.builder(root.root())
                .filter(Finder.excluding("glob:.*", "glob:target"))
                .build();
             RemoteFinderStreamDecoder decoder = new RemoteFinderStreamDecoder(in)) {
            int dirs = 0;
            int contents = 0;
            for (Found found : decoder) {
                //System.out.println(String.format("%s: %d contents", found.fullname(), found.contents().length));
                dirs++;
                contents += found.contents().length;
            }
            decoder.throwIfException();
            assertEquals(1 + 3 + 3*3, dirs); // root + d[1-3] + d[1-3]/e[1-3]
            assertEquals(5 + 3*13 + 3*3*10, contents); // root(5) + d[1-3](13) + d[1-3]/e[1-3](10)
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testDecoderOnActiualFile() {
        try (FileInputStream in = new FileInputStream("/Users/jthielens/d/vagrant/harmony/directory.listing");
             RemoteFinderStreamDecoder decoder = new RemoteFinderStreamDecoder(in)) {
            int dirs = 0;
            int contents = 0;
            for (Found found : decoder) {
                System.out.println(found);
                dirs++;
                contents += found.contents().length;
            }
            decoder.throwIfException();
            assertEquals(4, dirs);
            assertEquals(119, contents);
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }

}
