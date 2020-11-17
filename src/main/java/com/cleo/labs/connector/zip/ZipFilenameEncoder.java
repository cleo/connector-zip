package com.cleo.labs.connector.zip;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.cleo.connector.api.directory.Entry;
import com.cleo.connector.api.directory.Directory.Type;
import com.cleo.connector.api.helper.Attributes;
import com.cleo.labs.util.zip.PartitionedZipDirectory.Partition;

public class ZipFilenameEncoder {

    private List<Partition> partitions;

    private String format;
    private LocalDateTime now;

    private static final String ZIP_FILENAME = "part%%0%dd-%%s.zip";
    private static final Pattern ZIP_PATTERN = Pattern.compile("part(\\d+)-([^.]*)\\.zip");

    public ZipFilenameEncoder(List<Partition> partitions) {
        this.partitions = partitions;
        this.format = String.format(ZIP_FILENAME, String.valueOf(partitions.size()).length());
        this.now = Attributes.toLocalDateTime(System.currentTimeMillis());
    }

    public ZipFilenameEncoder() {
        this.partitions = null;
        this.format = null;
    }

    public String getFilename(int index) {
        if (partitions==null || index >= partitions.size()) {
            throw new IndexOutOfBoundsException();
        }
        return String.format(format, index+1, encodePartition(partitions.get(index)));
    }

    public Entry getEntry(String directory, int index) {
        Entry entry = new Entry(Type.file);
        entry.setPath(directory + getFilename(index));
        entry.setDate(now);
        entry.setSize(partitions.get(index).size());
        return entry;
    }

    public List<Entry> getEntries(String directory) {
        Entry[] entries = new Entry[partitions.size()];
        for (int i=0; i<entries.length; i++) {
            entries[i] = getEntry(directory, i);
        }
        return Arrays.asList(entries);
    }

    public Partition parseFilename(String filename) {
        Matcher m = ZIP_PATTERN.matcher(filename);
        if (m.matches()) {
            return decodePartition(m.group(2));
        }
        return null;
    }

    public static String encodePartition(Partition partition) {
        long[] longs = new long[partition.checkpoint().length+2];
        longs[0] = partition.count();
        longs[1] = partition.size();
        for (int i=0; i<partition.checkpoint().length; i++) {
            longs[2+i] = partition.checkpoint()[i];
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(encodeLongs(longs));
    }

    public static Partition decodePartition(String encoded) {
        long[] longs = decodeLongs(Base64.getUrlDecoder().decode(encoded));
        int count = (int)longs[0];
        long size = longs[1];
        int[] checkpoint = new int[longs.length-2];
        for (int i=0; i<checkpoint.length; i++) {
            checkpoint[i] = (int)longs[i+2];
        }
        return new Partition(size, count, checkpoint);
    }

    public static final byte[] ZERO_BYTE = new byte[] {0};

    public static byte[] encodeLong(long value) {
        if (value == 0) {
            return ZERO_BYTE;
        }
        int bits = 64 - Long.numberOfLeadingZeros(value);
        int frames = (bits + 6) / 7;
        byte[] bytes = new byte[frames];
        for (int frame=0; frame<frames; frame++) {
            bytes[frame] = (byte)((value >>> (frames-frame-1)*7) & 0x7F);
            if (frame < frames-1) {
                bytes[frame] |= 0x80;
            }
        }
        return bytes;
    }

    public static byte[] concatenate(byte[]...bytes) {
        int length = Stream.of(bytes).collect(Collectors.summingInt(b -> b.length));
        byte[] result = new byte[length];
        for (int i=0, offset=0; i<bytes.length; offset+=bytes[i++].length) {
            System.arraycopy(bytes[i], 0, result, offset, bytes[i].length);
        }
        return result;
    }

    public static byte[] encodeLongs(long[] values) {
        byte[][] encoded = new byte[values.length][];
        for (int i=0; i<values.length; i++) {
            encoded[i] = encodeLong(values[i]);
        }
        return concatenate(encoded);
    }

    public static long[] decodeLongs(byte[] bytes) {
        long[] longs = new long[0];
        long accumulator = 0;
        for (byte b : bytes) {
            accumulator = (accumulator << 7) | (b & 0x7f);
            if ((b & 0x80) == 0) {
                longs = Arrays.copyOf(longs, longs.length+1);
                longs[longs.length-1] = accumulator;
                accumulator = 0;
            }
        }
        if (accumulator != 0) {
            throw new IllegalArgumentException("improper encoding");
        }
        return longs;
    }

}
