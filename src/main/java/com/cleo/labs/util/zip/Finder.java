package com.cleo.labs.util.zip;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Stack;

public class Finder implements Iterator<Finder.Found> {

    public static class Found {
        public String[] path;
        public File file;
        public boolean directory;
        public Found(String[] path, File file, boolean directory) {
            this.path = path;
            this.file = file;
            this.directory = directory;
        }
    }

    private Stack<Directory> stack;

    private class Directory {
        private File[] files;
        private int file;
        private String[] parent;
        public Directory(String[] parent, File directory) {
            try {
                this.files = directory.listFiles();
            } catch (SecurityException e) {
                this.files = new File[0];
            }
            this.file = 0;
            this.parent = parent;
        }
        public Found next() {
            if (done()) {
                return null;
            }
            File next = files[file];
            file++;
            String[] path = Arrays.copyOf(parent, parent.length+1);
            path[path.length-1] = next.getName();
            boolean directory = next.isDirectory();
            if (directory) {
                stack.push(new Directory(path, next));
            }
            return new Found(path, next, directory);
        }
        public boolean done() {
            return files == null || file >= files.length;
        }
    }

    private void prune() {
        while (!stack.empty() && stack.peek().done()) {
            stack.pop();
        }
    }

    public Finder(File root) {
        this.stack = new Stack<>();
        stack.push(new Directory(new String[0], root));
        prune();
    }

    @Override
    public boolean hasNext() {
        return !stack.empty();
    }

    @Override
    public Found next() {
        Found result = stack.peek().next();
        prune();
        return result;
    }

}
