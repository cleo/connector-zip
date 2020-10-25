package com.cleo.labs.util.zip;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.google.gwt.thirdparty.guava.common.base.Joiner;

public class Finder implements Iterator<Finder.Found> {

    private static final Joiner SLASH = Joiner.on('/');

    public static class Found {
        public String[] path;
        public File file;
        public boolean directory;
        public String fullname;
        public Found(String[] path, File file, boolean directory) {
            this.path = path;
            this.file = file;
            this.directory = directory;
            this.fullname = SLASH.join(path);
            if (directory) {
                this.fullname += '/';
            }
        }
    }

    private Deque<Found> stack;
    private Predicate<Found> filter;
    private Found peeked;

    public enum DirectoryMode {include, exclude, excludeEmpty};
    private DirectoryMode directoryMode;
    private Deque<Found> pendingDirectories;

    private Found child(Found dir, File child, boolean directory) {
        String[] path = Arrays.copyOf(dir.path, dir.path.length+1);
        path[path.length-1] = child.getName();
        return new Found(path, child, directory);
    }

    private void push(Found dir) {
        if (dir.directory && filter.test(dir)) {
            File[] files = dir.file.listFiles();
            Stream.of(files)
                .filter(f -> !f.isDirectory())
                .map(f -> child(dir, f, false))
                .filter(filter)
                .forEach(stack::push);
            Stream.of(files)
                .filter(File::isDirectory)
                .map(f -> child(dir, f, true))
                .filter(filter)
                .forEach(stack::push);
        }
    }

    private void peek() {
        Found next = null;
        while (next==null && !stack.isEmpty()) {
            // see if we need to report out a pendingDirectory
            if (!pendingDirectories.isEmpty()) {
                Found peek = stack.peek();
                if (peek.directory) {
                    // if we are peeking at a directory, throw out already bypassed parents
                    while (!pendingDirectories.isEmpty() &&
                        !peek.fullname.startsWith(pendingDirectories.peekLast().fullname)) {
                        pendingDirectories.removeLast();
                    }
                } else {
                    // if we are peeking at a file, short-circuit the stack while we report out the parents
                    peeked = pendingDirectories.pollFirst(); // not null -- already know !isEmpty
                    return;
                }
            }
            // now go back into the stack to pull the next Found
            next = stack.pop();
            push(next);
            if (next.directory && directoryMode != DirectoryMode.include) {
                // don't report out this Found, or at least not right now
                if (directoryMode == DirectoryMode.excludeEmpty) {
                    pendingDirectories.add(next);
                }
                next = null;
            }
        }
        peeked = next;
    }

    public Finder(File root, Predicate<Found> filter, DirectoryMode directoryMode) {
        this.stack = new ArrayDeque<>();
        this.filter = filter;
        this.directoryMode = directoryMode;
        this.pendingDirectories = new ArrayDeque<>();
        Found start = new Found(new String[0], root, root.isDirectory());
        if (start.directory) {
            push(start);
        } else {
            stack.push(start);
        }
        peek();
    }

    public Finder(File root, Predicate<Found> filter) {
        this(root, filter, DirectoryMode.include);
    }

    public Finder(File root) {
        this(root, ALL);
    }

    @Override
    public boolean hasNext() {
        return peeked != null;
    }

    @Override
    public Found next() {
        Found result = peeked;
        peek();
        return result;
    }

    public static Predicate<Found> ALL = found->true;

    public static Predicate<Found> excluding(String...patterns) {
        PathMatcher[] matchers = new PathMatcher[patterns.length];
        FileSystem fs = FileSystems.getDefault();
        for (int i=0; i<patterns.length; i++) {
            matchers[i] = fs.getPathMatcher(patterns[i]);
        }
        return f->!Stream.of(matchers)
            .anyMatch(m -> m.matches(Paths.get("", f.path)));
    }

    public static Predicate<Found> including(String...patterns) {
        PathMatcher[] matchers = new PathMatcher[patterns.length];
        FileSystem fs = FileSystems.getDefault();
        for (int i=0; i<patterns.length; i++) {
            matchers[i] = fs.getPathMatcher(patterns[i]);
        }
        return f->f.directory || Stream.of(matchers)
            .anyMatch(m -> m.matches(Paths.get("", f.path)));
    }

}
