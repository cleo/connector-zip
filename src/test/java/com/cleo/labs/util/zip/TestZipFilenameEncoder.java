package com.cleo.labs.util.zip;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import com.cleo.labs.connector.zip.ZipFilenameEncoder;
import com.cleo.labs.util.zip.PartitionedZipDirectory.Partition;
import com.google.common.io.BaseEncoding;

public class TestZipFilenameEncoder {

    private void testEncodeValue(long l, String expected) {
        byte[] b = ZipFilenameEncoder.encodeLong(l);
        assertEquals(expected, BaseEncoding.base16().lowerCase().encode(b));
    }

    @Test
    public void testValueEncoder() {
        testEncodeValue(0, "00");
        testEncodeValue(1, "01");
        testEncodeValue(127, "7f");
        testEncodeValue(128, "8100");
        testEncodeValue(16383, "ff7f");
        testEncodeValue(16384, "818000");
    }

    @Test
    public void testConcatenate() {
        assertEquals(0, ZipFilenameEncoder.concatenate().length);
        assertEquals(0, ZipFilenameEncoder.concatenate(new byte[0]).length);
        assertArrayEquals(new byte[] {0,1,2}, ZipFilenameEncoder.concatenate(new byte[] {0}, new byte[] {1,2}));
    }

    @Test
    public void testValuesEncoder() {
        long[] values;
        values = new long[] {0, 1, Long.MAX_VALUE, Integer.MAX_VALUE};
        assertArrayEquals(values, ZipFilenameEncoder.decodeLongs(ZipFilenameEncoder.encodeLongs(values)));
    }

    @Test
    public void testFilenames() {
        List<Partition> partitions = Arrays.asList(
            new Partition(643232, 12, new int[] {0}),
            new Partition(124, 2, new int[] {0, 1, 13,0}));
        ZipFilenameEncoder encoder = new ZipFilenameEncoder(partitions);
        assertEquals("part1-DKehIAA.zip", encoder.getFilename(0));
        assertArrayEquals(new int[] {0, 1, 13, 0}, encoder.parseFilename(encoder.getFilename(1)).checkpoint());
    }

    @Ignore
    @Test
    public void testDecoder() {
        Partition p;
        p = new ZipFilenameEncoder().parseFilename("part3-0Teyo_4ZgaJu.zip");
        System.out.println(p);
        p = new ZipFilenameEncoder().parseFilename("part4-0Teyo_4fgfQl.zip");
        System.out.println(p);
    }
}
