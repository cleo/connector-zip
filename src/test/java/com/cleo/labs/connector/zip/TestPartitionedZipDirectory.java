package com.cleo.labs.connector.zip;

import static org.junit.Assert.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import com.cleo.labs.util.zip.Finder;
import com.cleo.labs.util.zip.PartitionedZipDirectory;
import com.cleo.labs.util.zip.PartitionedZipDirectory.Partition;
import com.cleo.labs.util.zip.ZipDirectoryInputStream;
import com.cleo.labs.util.zip.Finder.DirectoryMode;

public class TestPartitionedZipDirectory {

    private static final Path ZIP = Paths.get(System.getProperty("user.home"), "Downloads", "test.zip");

    @Ignore
    @Test
    public void testSingle() throws IOException {
        long start = System.currentTimeMillis();
        long size = 0;
        Path root = Paths.get(".");
        root = Paths.get(System.getProperty("user.home"),"d/vagrant/cache/zip");
        try (ZipDirectoryInputStream zip = ZipDirectoryInputStream.builder(root.toFile())
                .opener(f -> { return new FileInputStream(f.file); })
                .filter(Finder.excluding("glob:.git/**","glob:**/*.class"))
                .directoryMode(DirectoryMode.exclude)
                .build()) {
            size = Files.copy(zip, ZIP, StandardCopyOption.REPLACE_EXISTING);
        }
        PartitionedZipDirectory zip = PartitionedZipDirectory.builder(root.toFile())
                .opener(f -> { /*System.out.println("opening "+f.fullname);*/ return new FileInputStream(f.file); })
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

    @Ignore
    @Test
    public void testParts() throws IOException {
        long start = System.currentTimeMillis();
        long threshold = 1024 * 1024;
        Path root = Paths.get(".");
        root = Paths.get(System.getProperty("user.home"),"d/vagrant/cache/zip");
        PartitionedZipDirectory zip = PartitionedZipDirectory.builder(root.toFile())
                .opener(f -> { /*System.out.println("opening "+f.fullname);*/ return new FileInputStream(f.file); })
                .filter(Finder.excluding("glob:.git/**","glob:**/*.class"))
                .threshold(threshold)
                .directoryMode(DirectoryMode.exclude)
                .build();
        List<Partition> partitions = zip.partitions();
        partitions.forEach(p -> System.err.println(hms(start)+": "+p));
        assertTrue(partitions.size() > 1);
        for (int i=0; i<partitions.size(); i++) {
            try (ZipDirectoryInputStream zipn = ZipDirectoryInputStream.builder(root.toFile())
                    .opener(f -> { /*System.out.println("reopening "+f.fullname);*/ return new FileInputStream(f.file); })
                    .filter(Finder.excluding("glob:.git/**","glob:**/*.class"))
                    .directoryMode(DirectoryMode.exclude)
                    .restart(partitions.get(i).checkpoint())
                    .limit(partitions.get(i).count())
                    .build()) {
                Path output = Paths.get(System.getProperty("user.home"), "Downloads", "test"+i+".zip");
                long size = Files.copy(zipn, output, StandardCopyOption.REPLACE_EXISTING);
                assertEquals(partitions.get(i).size(), size);
            }
        }
        System.out.println(hms(start));
        //assertEquals(size, partitions.get(0).size());
    }

    @Ignore
    @Test
    public void ignore() {
    }

}
