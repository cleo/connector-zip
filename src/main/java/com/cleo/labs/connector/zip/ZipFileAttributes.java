package com.cleo.labs.connector.zip;

import java.io.IOException;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Zip file attribute views
 */
public class ZipFileAttributes implements DosFileAttributes, DosFileAttributeView {
    private String zipFileName;
    private FileTime now = FileTime.from(new Date().getTime(), TimeUnit.MILLISECONDS);

    public ZipFileAttributes(String zipFileName) throws IOException {
        this.zipFileName = zipFileName;
    }

    @Override
    public FileTime lastModifiedTime() {
        return now;
    }

    @Override
    public FileTime lastAccessTime() {
        return now;
    }

    @Override
    public FileTime creationTime() {
        return now;
    }

    @Override
    public boolean isRegularFile() {
        return true; // the pretend directory.zip file is a file
    }

    @Override
    public boolean isDirectory() {
        return false; // the pretend directory.zip file is a file
    }

    @Override
    public boolean isSymbolicLink() {
        return false; // the pretend directory.zip file is a file
    }

    @Override
    public boolean isOther() {
        return false; // the pretend directory.zip file is a file
    }

    @Override
    public long size() {
        return -1L;
    }

    @Override
    public Object fileKey() {
        return null;
    }

    @Override
    public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        if (lastModifiedTime != null || lastAccessTime != null || createTime != null) {
            throw new UnsupportedOperationException("setTimes() not supported on "+zipFileName);
        }
    }

    @Override
    public String name() {
        return zipFileName;
    }

    @Override
    public DosFileAttributes readAttributes() throws IOException {
        return this;
    }

    @Override
    public void setReadOnly(boolean value) throws IOException {
        throw new UnsupportedOperationException("setHidden() not supported on "+zipFileName);
    }

    @Override
    public void setHidden(boolean value) throws IOException {
        throw new UnsupportedOperationException("setHidden() not supported on "+zipFileName);
    }

    @Override
    public void setSystem(boolean value) throws IOException {
        throw new UnsupportedOperationException("setSystem() not supported on "+zipFileName);
    }

    @Override
    public void setArchive(boolean value) throws IOException {
        throw new UnsupportedOperationException("setArchive() not supported on "+zipFileName);
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public boolean isArchive() {
        return false;
    }

    @Override
    public boolean isSystem() {
        return false;
    }

}
