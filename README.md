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
Select | A single file/path pattern to include while zipping | A `glob:` or `regex:` pattern | none
Remote Directory List | A directory listing for differential replication | Typically a `pipe:` URI | none
Dont Zip Empty Directories | Select to skip empty directories while zipping | on or off | off
Unzip Mode | Normal unzip, or log or preflight test options | `unzip`, `unzip and log`, `log` or `preflight` | `unzip`
Suppress Directory Creation | Unzip files, but don't create directories | on or off | off
Unzip Root Files Last | Save top-level files in a temporary folder until the end | on or off | off

### Exclusions

Use exclusions to prevent files or directories from being included in a zip file during a zip operation, or to prevent files in the zip archive from being unzipped. The exclusion list is managed as a table of Exclusion Pattern. Both regular expression (`regex`) and wildcard (`glob`) patterns are supported. See the Java [getPathMatcher](https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystem.html#getPathMatcher-java.lang.String-) documentation for more details. If neither a `glob:` nor a `regex:` prefix is provided, `glob:` is the default.

Note that while zipping, if you exclude a directory then all files in that directory will be excluded. Since zip files are not required to be organized by directory, each entry is filtered on its own&mdash;to exclude an entire directory while unzipping, include a pattern that explicitly excludes the directory contents. For example, to exclude the `.hidden` directory and all its contents:

* zip exclusion: `glob:.hidden`
* unzip exclusion: `glob:.hidden{,/*}`

### Select

Use a select expression while zipping to include a single file or path name or pattern in the zip file. Both the _Select_ pattern and _Exclusions_ patterns must be satisfied for files to be included (so don't exclude your select pattern).

_Select_ is convenient to use in an action or URI to pick a specific file to zip from a _Root Path_ covered by a Zip connector:

```
SET Zip.Select=file.txt
GET file.zip
```

will create `file.zip` in the inbox containing `file.txt` from _Root Path_, or

```
LCOPY zip:connection/file.zip?zip.select=file.txt&zip.rootpath=/some/path /destination/
```

will create `/destination/file.zip` containing `file.txt` from `/some/path`, overriding both the _Root Path_ and _Select_ from a Zip connection named `connection`.

### Replication

The zip connector can be used to compare a remote directory's contents against the (filtered) root path, only zipping additions and changes to be unzipped and overlaid at the remote target.

Directory listings are produced by zip connectors when a `GET directory.listing` command is processed, usually using a zip uri like `zip:connection/directory.listing` or through the pipe connector `pipe:pipe/directory.listing` in conjunction with an HSP (JetSonic) replication setup. The format is nearly human readable, comprising a sequence of JSON encoded directory listings, each preceded by its byte length encoded in 4 binary bytes. The root directory appears first, followed by the subdirectories in a pre-order traversal (parents before children).

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

### Unzip and ZipSlip

An implementation of unzip is vulnerable to the [ZipSlip](https://snyk.io/research/zip-slip-vulnerability) attack when it does not carefully process the paths encoded in the zip file to ensure files are not created outside the intended destination folder. The Zip Connector defends against this attack by editing the encoded paths by:

* stripping any leading "root" prefix, including leading `/` or `\` characters and any prefix that looks like a drive letter like `x:`, and
* stripping any embedded `.` or `..` path elements, whether using `/` or `\` as the path delimiter, from the path.

The file is extracted to the resulting edited path, prefixed with the intended _Root Path_.

## Using Zip Connector Commands ##

In addition to `GET` and `PUT`, the connector supports the `DIR` command, which allows `GET *` to work properly. The `DIR` command returns a single entry with the name of the directory appended with `.zip` (or `directory.xip` if the directory name cannot be determined). The directory is marked as a plain file with unknown size (-1).

When processing a command `PUT path/file`, the `file` name is discarded, but the `path` is processed as a subfolder of the root path. If `path` does not exist, the `PUT` command will create it (and all subfolders based on the contents of the file being unzipped) as needed.

Note that the `PUT` command will fail if file or path names in the file being unzipped attempt to write outside the root folder, e.g. through the use of absolute pathnames or `..` prefixes.

## Using the Zip Connector as a URI ##

The URI facility allows a reference to a zip connection to be used as the source or target of a `PUT`, `GET`, or `LCOPY` command in another host or connection, or its inbox or outbox. 

In a directory context (like inbox or outbox) use the syntax:

* `zip:alias` to refer to zip from/unzip to the root path
* `zip:alias/folder/` to refer to zip from/unzip to a subfolder of the root path

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