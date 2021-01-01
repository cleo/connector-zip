package com.cleo.labs.util.zip;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.junit.Test;

import com.google.common.io.ByteStreams;

public class TestFillInputStream {

    @Test
    public void testZero() {
        try (InputStream in = new FillInputStream((byte)' ', 0L)) {
            long size = ByteStreams.exhaust(in);
            assertEquals(0L, size);
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testTenSpaces() {
        long ten = 10L;
        try (InputStream in = new FillInputStream((byte)' ', ten)) {
            byte[] buf = new byte[(int)ten];
            ByteStreams.readFully(in, buf);
            assertEquals(-1, in.read());
            byte[] spaces = new byte[(int)ten];
            Arrays.fill(spaces, (byte)' ');
            assertArrayEquals(spaces, buf);
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }
    @Test
    public void testUnlimited() {
        int infinity = 1000;
        try (InputStream in = new FillInputStream((byte)' ')) {
            byte[] buf = new byte[infinity];
            ByteStreams.readFully(in, buf);
            assertEquals((byte)' ', in.read());
            byte[] spaces = new byte[infinity];
            Arrays.fill(spaces, (byte)' ');
            assertArrayEquals(spaces, buf);
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }

}
