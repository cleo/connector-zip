# Zip Connector #

The zip connector produces a single zip file from an entire directory on a `GET` operation, or unzips a zip file into a directory on a `PUT`.

## Installation ##

The zip connector is packaged in a single jar `zip-version.jar`, which should be placed in the `lib/api/connector` directory within the product installation directory. You may also unzip `zip-version-distribution.zip` from the installation directory, which will place the jar in the correct directory. You must restart in order for the jar to be loaded.

## Configuration ##

There are five important configuration settings for the zip connector.

Property | Description | Value | Default
---------|-------------|-------|--------
Root Path | The directory to zip on `GET` or in which to unzip on `PUT` | A directory path | Unspecified
Compression Level | The zip compression level | `none`, `1`-`9`, or `default` | `default`
Exclusions | A list of file/path patterns to exclude from zipping and unzipping | A table of exclusion patterns | none
Dont Zip Empty Directories | Select to skip empty directories while zipping | on or off | off
Zip Size Threshold | Set to split large zip files into parts when the size threshold is crossed | a numeric value followed by `k`, `m`, `g`, or `t`, or `0` for unlimited | `0`
Unzip Mode | Normal unzip, or log or preflight test options | `unzip`, `log` or `preflight` | `unzip`
Suppress Directory Creation | Unzip files, but don't create directories | on or off | off
Unzip Root Files Last | Save top-level files in a temporary folder until the end | on or off | off

### Exclusions

Use exclusions to prevent files or directories from being included in a zip file during a zip operation, or to prevent files in the zip archive from being unzipped. The exclusion list is managed as a table of Exclusion Pattern. Both regular expression (`regex`) and wildcard (`glob`) patterns are supported. See the Java [getPathMatcher](https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystem.html#getPathMatcher-java.lang.String-) documentation for more details.

Note that while zipping, if you exclude a directory then all files in that directory will be excluded. Since zip files are not required to be organized by directory, each entry is filtered on its own&mdash;to exclude an entire directory while unzipping, include a pattern that explicitly excludes the directory contents. For example, to exclude the `.hidden` directory and all its contents:

* zip exclusion: `glob:.hidden`
* unzip exclusion: `glob:.hidden{,/*}`

### Zip Thresholds

The zip connector is configured with a _Root Path_ that points to the directory of files to be zipped. It creates a projection of the entire directory as a synthetic directory consisting of one or more zip archives containing the (possibly filtered) contents of the entire _Root Path_.

By default when zipping the zip connector creates a single zip archive, so the projected directory consists of a single zip file (if you run a `DIR *` command, you will see a single file named `part1-code.zip`, where `code` is a sequence of characters encoding the file's size and number of entries).

If you wish to partition a large archive into pieces, you can set the _Zip Size Threshold_ to a byte size. If the connector detects that a zip archive has reached the threshold, it will finish adding the current file and will then close the file and stop zipping, leaving the remaining files for subsequent zip archives. The projected directory will then contain a set of files named `partn-code.zip`, where `n` cycles from `1` to the number of parts, and the `code` for each file encodes the file's size, number of entries, and the starting point in the _Root Path_ tree where this partition begins.

With or without thresholding, a `GET *` command will retrieve the entire contents of _Root Path_ compressed into some number of zip archives. Unlike multi-part zip archives, each `partn-code.zip` file is a complete stand-alone zip archive that can be individually unzipped indepenently from the other parts. If you unzip the entire set of partitions, the entire _Root Path_ directory will be faithfully reconstructed.

### Unzip Modes and Preflight

Set the _Unzip Mode_ to `log` to analyze a zip file by logging files and directories that would be created in the default `unzip` mode. Set _Unzip Mode_ to `preflight` to test the intended unzip destination (the _Root Path_) for existing files and directories that would be overwritten in `unzip` mode, failing the "transfer" at the first conflict detected.

Use `preflight` in a JavaScript action in a manner such as:

```
importPackage(com.cleo.lexicom.external);
var action = ISessionScript.getActionController();
var rc = 0;
action.execute("SET Zip.UnzipMode=preflight");
if (action.execute("PUT test.zip")) {
    action.execute("SET Zip.UnzipMode=unzip");
    action.execute("PUT test.zip");
} else {
    rc = 1;
}
rc;
```

### Unzip and Cloud Storage

Cloud storage infrastructures such as Amazon S3, Azure Blob Storage or Google Cloud Storage are keyed object stores that simulate a folder hierarchy with naming conventions and zero-byte objects. Select the _Suppress Directory Creation_ option to prevent the creation of folders while unzipping.

### Unzip and Folder Monitors

In many cases the zip archive represents a folder structure of files that, when unzipped, will be processed by an external system. When the files in subfolders are understood to be attachments to a larger transaction whose index files are in the top level of the archive, it is important that the subfolders be unzipped first and the top level files last. Select the _Unzip Root Files Last_ option to have top level files stored in a temporary subfolder of the target directory (it will be given a name based on a GUID like `.ee0eabd6-f3aa-491c-a30a-35b09b835f95`) until the zip file is completely unzipped, at which time the top level files will be moved from the temporary subfolder to the top level and the temporary folder will be removed.

## Using Zip Connector Commands ##

In addition to `GET` and `PUT`, the connector supports the `DIR` command, which allows things like `GET *` to work properly. Directory listing starts at the root path, and the true contents of the directory are listed with the following alterations:

* only directories are listed, allowing the directory structure under the root path to be navigated, and
* a pseudo-file `directory.zip` is listed in every directory and sub-directory.

When processing a command `GET path/file`, the connector ignores the `file` name, but starts zipping at the `path` subpath from the root path. So:

Command | Result
--------|-------
`GET directory.zip` | a zip file `directory.zip` containing all files in the root path is placed in the inbox
`GET dir/foo.zip` | a zip file `foo.zip` containing all files in the `dir` subfolder is placed in the inbox
`GET *` | the same as `GET directory.zip`
`GET * /path/allfiles.zip` | a zip file `allfiles.zip` contianing all files in the root path is placed at `/path`

When processing a command `PUT path/file`, the behavior is similar in that the `file` name is discarded, but the `path` is processed as a subfolder of the root path. If `path` does not exist, the `PUT` command will create it (and all subfolders based on the contents of the file being unzipped) as needed.

Note that the `PUT` command will fail if file or path names in the file being unzipped attempt to write outside the root folder, e.g. through the use of absolute pathnames or `..` prefixes.

## Using the Zip Connector as a URI ##

The URI facility allows a reference to a zip connection to be used as the source or target of a `PUT`, `GET`, or `LCOPY` command in another host or connection, or its inbox or outbox. 

In a directory context (like inbox or outbox) use the syntax:

* `zip:alias` to refer to zip from/unzip to the root path
* `zip:alias/folder/` to refer to zip from/unzip to a subfolder of the root path

In a specific file context (like `PUT`, `GET` or `LCOPY` of a single file) use the syntax:

* `zip:alias/file.zip` to refer to the root path as a file named `file.zip`
* `zip:alias/folder/file.zip` to add a subfolder

When using the zip connector as a URI, keep in mind that the URI mechanism depends on the standard `send` and `receive` actions that were created when you initially activated the connection:

#### `send` action

```
# Send files to remote server
PUT -DEL *
```

#### `receive` action

```
# Receive all files from remote server
GET *
```

Of course the comments `#` are not important.