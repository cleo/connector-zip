package com.cleo.labs.util.zip;

import java.io.File;
import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.google.common.base.Joiner;

public class Found {
    private static final Joiner SLASH = Joiner.on('/');

    private String[] path;
    private File file;
    private boolean directory;
    private long length;
    private long modified;
    private int depth;
    private int index;
    private String fullname;
    private Found[] contents;

    public String[] path() {
        return path;
    }
    public File file() {
        return file;
    }
    @JsonGetter
    public boolean directory() {
        return directory;
    }
    @JsonGetter
    public long length() {
        return length;
    }
    @JsonGetter
    public long modified() {
        return modified;
    }
    public int depth() {
        return depth;
    }
    public int index() {
        return index;
    }
    @JsonGetter
    public String fullname() {
        return this.fullname;
    }
    @JsonGetter
    @JsonInclude(Include.NON_NULL)
    public Found[] contents() {
        return contents;
    }

    @JsonSetter
    public Found directory(boolean directory) {
        this.directory = directory;
        return this;
    }
    @JsonSetter
    public Found length(long length) {
        this.length = length;
        return this;
    }
    @JsonSetter
    public Found modified(long modified) {
        this.modified = modified;
        return this;
    }
    public Found index(int index) {
        this.index = index;
        return this;
    }
    @JsonSetter
    public Found fullname(String fullname) {
        this.fullname = fullname;
        return this;
    }
    @JsonSetter
    public Found contents(Found[] contents) {
        this.contents = contents;
        return this;
    }

    public Found() {
        this.path = null;
        this.file = null;
        this.directory = false;
        this.length = -1;
        this.modified = -1;
        this.depth = -1;
        this.index = -1;
        this.fullname = null;
        this.contents = null;
    }

    public Found(String[] path, File file, int depth, int index) {
        this.path = path;
        this.file = file;
        this.directory = file.isDirectory();
        this.length = file.length();
        this.modified = file.lastModified();
        this.depth = depth;
        this.index = index;
        this.fullname = SLASH.join(path);
        if (directory) {
            this.fullname += '/';
        }
        this.contents = null;
    }

    public Found child(File child, int index) {
        String[] childpath = Arrays.copyOf(this.path, this.path.length+1);
        childpath[childpath.length-1] = child.getName();
        return new Found(childpath, child, this.depth+1, index);
    }
    public Found child(File child) {
        return child(child, 0);
    }
    public boolean contains(Found found) {
        return directory &&
                (path.length==0 || found.fullname.startsWith(fullname));
    }
    public boolean containsAFile() {
        return contents!=null && contents.length>0 && !contents[contents.length-1].directory;
    }
    @Override
    public String toString() {
        return fullname+" ("+depth+"."+index+")";
    }
}