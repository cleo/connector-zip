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
    private static final Path[] ZIPS = new Path[] {
            Paths.get(System.getProperty("user.home"), "Downloads", "testA.zip"),
            Paths.get(System.getProperty("user.home"), "Downloads", "testB.zip"),
            Paths.get(System.getProperty("user.home"), "Downloads", "testC.zip")};

    @Test
    public void testSingle() throws IOException {
        long size = 0;
        try (ZipDirectoryInputStream zip = ZipDirectoryInputStream.builder(Paths.get(".").toFile())
                .opener(f -> { return new FileInputStream(f.file); })
                .filter(Finder.excluding("glob:.git/**","glob:**/*.class"))
                .directoryMode(DirectoryMode.exclude)
                .build()) {
            size = Files.copy(zip, ZIP, StandardCopyOption.REPLACE_EXISTING);
        }
        PartitionedZipDirectory zip = PartitionedZipDirectory.builder(Paths.get(".").toFile())
                .opener(f -> { /*System.out.println("opening "+f.fullname);*/ return new FileInputStream(f.file); })
                .filter(Finder.excluding("glob:.git/**","glob:**/*.class"))
                .threshold(0)
                .directoryMode(DirectoryMode.exclude)
                .build();
        List<Partition> partitions = zip.partitions();
        assertEquals(1, partitions.size());
        assertEquals(size, partitions.get(0).size());
    }

    @Ignore
    @Test
    public void testParts() throws IOException {
        long threshold = 100_000;
        PartitionedZipDirectory zip = PartitionedZipDirectory.builder(Paths.get(".").toFile())
                .opener(f -> { /*System.out.println("opening "+f.fullname);*/ return new FileInputStream(f.file); })
                .filter(Finder.excluding("glob:.git/**","glob:**/*.class"))
                .threshold(threshold)
                .directoryMode(DirectoryMode.exclude)
                .build();
        List<Partition> partitions = zip.partitions();
        partitions.forEach(System.out::println);
        assertTrue(partitions.size() > 1);
        for (int i=0; i<partitions.size(); i++) {
            try (ZipDirectoryInputStream zipn = ZipDirectoryInputStream.builder(Paths.get(".").toFile())
                    .opener(f -> { /*System.out.println("reopening "+f.fullname);*/ return new FileInputStream(f.file); })
                    .filter(Finder.excluding("glob:.git/**","glob:**/*.class"))
                    .directoryMode(DirectoryMode.exclude)
                    .restart(partitions.get(i).checkpoint())
                    .limit(partitions.get(i).count())
                    .build()) {
                long size = Files.copy(zipn, ZIPS[i], StandardCopyOption.REPLACE_EXISTING);
                assertEquals(partitions.get(i).size(), size);
            }
        }
        //assertEquals(size, partitions.get(0).size());
    }

}
