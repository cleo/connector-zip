package com.cleo.labs.util.zip;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import com.cleo.labs.util.zip.Finder.DirectoryMode;
import com.cleo.labs.util.zip.PartitionedZipDirectory.Partition;
import com.google.common.io.ByteStreams;

public class TestPartitionedZipDirectory {

    @Test
    public void testSingle() throws IOException {
        long start = System.currentTimeMillis();
        long size = 0;
        MockBagOFiles root = new MockBagOFiles().files("f%d.txt", 1, 50000, 10000, (byte)' ');
        try (ZipDirectoryInputStream zip = ZipDirectoryInputStream.builder(root.root())
                .opener(root.opener())
                .filter(Finder.excluding("glob:.git/**","glob:**/*.class"))
                .directoryMode(DirectoryMode.exclude)
                .build()) {
            size = ByteStreams.exhaust(zip);
        }
        PartitionedZipDirectory zip = PartitionedZipDirectory.builder(root.root())
                .opener(root.opener())
                .filter(Finder.excluding("glob:.git/**","glob:**/*.class"))
                .threshold(0)
                .directoryMode(DirectoryMode.exclude)
                .build();
        List<Partition> partitions = zip.partitions();
        partitions.forEach(p -> System.out.println(hms(start)+": "+p));
        assertEquals(1, partitions.size());
        assertEquals(size, partitions.get(0).size());
    }

    private static String hms(long start) {
        long millis = System.currentTimeMillis()-start;
        long s = millis / 1000;
        long m = s / 60;
        long h = m / 60;
        return String.format("%1d:%02d:%02d.%03d", h, m%60, s%60, millis%1000);
    }

    public void partTester(DirectoryMode directoryMode) throws IOException {
        long start = System.currentTimeMillis();
        long threshold = 1024 * 1024;
        MockBagOFiles root = new MockBagOFiles().files("f%d.txt", 1, 50000, 10000, (byte)' ');
        PartitionedZipDirectory zip = PartitionedZipDirectory.builder(root.root())
                .opener(root.opener())
                .filter(Finder.excluding("glob:.git/**","glob:**/*.class"))
                .threshold(threshold)
                .directoryMode(directoryMode)
                .build();
        List<Partition> partitions = zip.partitions();
        partitions.forEach(p -> System.err.println(hms(start)+": "+p));
        assertTrue(partitions.size() > 1);
        for (int i=0; i<partitions.size(); i++) {
            try (ZipDirectoryInputStream zipn = ZipDirectoryInputStream.builder(root.root())
                    .opener(root.opener())
                    .filter(Finder.excluding("glob:.git/**","glob:**/*.class"))
                    .directoryMode(directoryMode)
                    .restart(partitions.get(i).checkpoint())
                    .limit(partitions.get(i).count())
                    .build()) {
                long size = ByteStreams.exhaust(zipn);
                assertEquals(partitions.get(i).size(), size);
            }
        }
        System.out.println(hms(start));
    }

    //@Ignore
    @Test
    public void testPartsExclude() throws IOException {
        partTester(DirectoryMode.exclude);
    }

    //@Ignore
    @Test
    public void testPartsExcludeEmpty() throws IOException {
        partTester(DirectoryMode.excludeEmpty);
    }

    @Ignore
    @Test
    public void ignore() {
    }

}
