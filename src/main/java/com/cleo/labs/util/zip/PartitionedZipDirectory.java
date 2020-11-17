package com.cleo.labs.util.zip;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.Deflater;

import com.cleo.labs.util.zip.Finder.DirectoryMode;
import com.cleo.labs.util.zip.Finder.Found;
import com.cleo.labs.util.zip.ZipDirectoryInputStream.Opener;
import com.google.common.io.ByteStreams;

public class PartitionedZipDirectory {

    private File path;
    private Opener opener;
    private int level;
    private Predicate<Found> filter;
    private DirectoryMode directoryMode;
    private long threshold;

    public PartitionedZipDirectory(File path,
            Opener opener,
            int level,
            Predicate<Found> filter,
            DirectoryMode directoryMode,
            long threshold) throws IOException {
        this.path = path;
        this.opener = opener;
        this.level = level;
        this.filter = filter;
        this.directoryMode = directoryMode;
        this.threshold = threshold;
    }

    public static class Builder {
        private File path = null;
        private Opener opener = f -> new ByteArrayInputStream(new byte[0]);
        private int level = Deflater.DEFAULT_COMPRESSION;
        private Predicate<Found> filter = Finder.ALL;
        private DirectoryMode directoryMode = DirectoryMode.include;
        private long threshold = 0L;
        public Builder(File path) {
            this.path = path;
        }
        public Builder opener(Opener opener) {
            this.opener = opener;
            return this;
        }
        public Builder level(int level) {
            this.level = level;
            return this;
        }
        public Builder filter(Predicate<Found> filter) {
            this.filter = filter;
            return this;
        }
        public Builder directoryMode(DirectoryMode directoryMode) {
            this.directoryMode = directoryMode;
            return this;
        }
        public Builder threshold(long threshold) {
            this.threshold = threshold;
            return this;
        }
        public PartitionedZipDirectory build() throws IOException {
            return new PartitionedZipDirectory(path, opener, level, filter, directoryMode, threshold);
        }
    }

    public static Builder builder(File path) {
        return new Builder(path);
    }

    public static class Partition {
        private long size;
        private int count;
        private int[] checkpoint;
        public long size() {
            return size;
        }
        public int count() {
            return count;
        }
        public int[] checkpoint() {
            return checkpoint;
        }
        public Partition(long size, int count, int[] checkpoint) {
            this.size = size;
            this.count = count;
            this.checkpoint = checkpoint;
        }
        @Override
        public String toString() {
            StringBuilder s = new StringBuilder();
            s.append("Partition{size=")
             .append(size)
             .append(", count=")
             .append(count)
             .append(", checkpoint=[");
            for (int c : checkpoint) {
                s.append(c)
                 .append(", ");
            }
            if (checkpoint.length > 0) {
                s.setLength(s.length()-2);
            }
            s.append("]}");
            return s.toString();
        }
    }

    public List<Partition> partitions() throws IOException {
        List<Partition> partitions = new ArrayList<>();
        int[] checkpoint = new int[0];
        do {
            ZipDirectoryInputStream zip = ZipDirectoryInputStream.builder(path)
                    .opener(opener)
                    .level(level)
                    .filter(filter)
                    .directoryMode(directoryMode)
                    .restart(checkpoint)
                    .build();
            InputStream in = zip;
            if (threshold > 0) {
                in = ByteStreams.limit(in, threshold);
            }
            long size = ByteStreams.exhaust(in);
            zip.limit(zip.count());
            size += ByteStreams.exhaust(zip);
            partitions.add(new Partition(size, zip.count(), checkpoint));
            checkpoint = zip.checkpoint();
            zip.close();
        } while (checkpoint.length > 0);
        return partitions;
    }
}
