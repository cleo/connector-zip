package com.cleo.labs.util.zip;

import java.util.stream.Stream;

import com.google.common.base.Joiner;

public class PathUtil {

    /**
     * Converts \ to / and makes sure there is a trailing /.
     * @param dir a possibly messed up directory name
     * @return a cleaned up directory name
     */
    public static String asDirectory(String dir) {
        return dir.replaceFirst("(?<![/\\\\])$", "/");
    }

    /**
     * Strips off any leading root indicator: a drive-letter: and/or leading \ or /.
     * @param dir a possibly messed up directory name
     * @return a cleaned up directory name
     */
    public static String stripRoot(String dir) {
        return dir.replaceFirst("^(?:\\p{Alpha}:|/|\\\\)*", "");
    }

    /**
     * Returns the directory prefix of a file path, if any.
     * Trims off any trailing separator (/ or \), then removes
     * the final /-or-\-separated element.
     * @param file a possibly messed up file path
     * @return the (possibly empty, but if not ending in /) path prefix
     */
    public static String justDirectory(String file) {
        // this is capture(stripRoot + (name + slash(es))*) + name + slashes
        // all replaced by the capture (dropping the final name + slashes)
        return file.replaceFirst("^((?:\\p{Alpha}:|/|\\\\)*(?:[^/\\\\]+[/\\\\]+)*?)"+"[^/\\\\]+[/\\\\]*$", "$1");
    }

    /**
     * Returns the file path stripped of {@link #justDirectory}.
     * @param file a possibly messed up file path
     * @return the (possibly empty) file name (with no leading or trailing slashes)
     */
    public static String justFile(String file) {
        return file.replaceFirst("^((?:\\p{Alpha}:|/|\\\\)*(?:[^/\\\\]+[/\\\\]+)*?)"+"(?:([^/\\\\]+)[/\\\\]*)?$", "$2");
    }

    /**
     * Returns the result of {@link #justDirectory(String)}, but
     * using a default value in place of the empty result.
     * @param file a possibly messed up file path
     * @param ifEmpty the result to use in place of an empty result
     * @return the result
     */
    public static String justFile(String file, String ifEmpty) {
        String result = justFile(file);
        if (result.isEmpty()) {
            result = ifEmpty;
        }
        return result;
    }

    /**
     * Returns the appropriate path separator "slash" to use for
     * a path. In general this is {@code /}, but if
     * <ul><li>the path begins with &lt;drive-letter&gt;:, or</li>
     *     <li>the first separator in the path is {@code \}</li>
     *  </ul>
     *  then {@code \} is returned.
     * @param path a (possibly {@code null}) path to examine
     * @return {@code "/"} or {@code "\\"}
     */
    public static String slash(String path) {
        if (path==null) {
            return "/";
        }
        if (path.matches("^\\p{Alpha}:.*")) {
            return "\\";
        }
        int slash = path.indexOf('/');
        int bslash = path.indexOf('\\');
        if (slash == -1) {
            return bslash == -1 ? "/" : "\\";
        } else if (bslash == -1) {
            return "/";
        } else {
            return slash < bslash ? "/" : "\\";
        }
    }

    public static final String ANY_SEPARATOR = "[/\\\\]+";
    public static final String DEFAULT_SEPARATOR = "/";

    /**
     * Splits a path into path elements separated by
     * {@code /}, @{code \}, or any combination. Any trailing
     * empty element (which would be caused by a trailing
     * separator) is not included. If the path is {@code null},
     * an empty array is returned.
     * @param path the (possibly {@code null} path to split
     * @return the (possibly empty but never {@code null} array of path elements
     */
    public static String[] split(String path) {
        if (path == null) {
            return new String[0];
        }
        return path.split(ANY_SEPARATOR);
    }

    /**
     * Joins a split path into a string {@code /} separated
     * string. {@code null} returns an empty string.
     * @param path a (possibly {@code null}) array of strings to join
     * @return the (possibly empty but never {@code null}) joined string
     */
    public static String join(String...path) {
        if (path == null) {
            return "";
        }
        return Joiner.on(DEFAULT_SEPARATOR).skipNulls().join(path);
    }

    /**
     * Joins a split path to a parent.
     * @param parent the (possibly {@code null} or empty) parent
     * @param path the (possibly {@code null} or empty) path to append
     * @return the (possibly empty but never {@code null}) joined string
     */
    public static String append(String parent, String...path) {
        if (parent==null || parent.isEmpty()) {
            return join(path);
        } else if (path==null || path.length==0) {
            return parent;
        } else {
            return parent+DEFAULT_SEPARATOR+join(path);
        }
    }

    /**
     * Parses a string into a "safe" path, where "safety" includes:
     * <ul><li>stripping any root prefix (see {@link #stripRoot})</li>
     *     <li>breaking the path into elements using {@code /} or {@code \} separators</li>
     *     <li>discarding any {@code .} or {@code ..} elements of the path</li>
     *  </ul>
     *  Returns the parsed string as a String array. Note that trailing path
     *  separators are ignored/discarded. A {@code null} or empty path will
     *  result in a array of length 0.
     * @param path the path to parse safely
     * @return a (possibly empty but never null) array of Strings
     */
    public static String[] safePath(String path) {
        if (path==null) {
            return new String[0];
        }
        return Stream.of(stripRoot(path)                           // strip the root
                .split(ANY_SEPARATOR))                             // split on one or more slash
                .filter(s -> !(s.equals(".")||s.equals("..")))     // remove . and ..
                .toArray(String[]::new);
    }

    /**
     * Utility class.
     */
    private PathUtil() {
    }

}
