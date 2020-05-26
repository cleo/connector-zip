# Zip Connector #

The zip connector produces a single zip file from an entire directory on a `GET` operation, or unzips a zip file into a directory on a `PUT`.

## Installation ##

The zip connector is packaged in a single jar `zip-version.jar`, which should be placed in the `lib/api/connector` directory within the product installation directory. You may also unzip `zip-version-distribution.zip` from the installation directory, which will place the jar in the correct directory. You must restart in order for the jar to be loaded.

## Configuration ##

There are two important configuration settings for the zip connector.

Property | Description | Value | Default
---------|-------------|-------|--------
Root Path | The directory to zip on `GET` or in which to unzip on `PUT` | A directory path | Unspecified
Compression Level | the zip compression level | `none`, `1`-`9`, or `default` | `default`

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