package com.cleo.labs.connector.zip;

import static com.cleo.connector.api.command.ConnectorCommandName.ATTR;
import static com.cleo.connector.api.command.ConnectorCommandName.DIR;
import static com.cleo.connector.api.command.ConnectorCommandName.GET;
import static com.cleo.connector.api.command.ConnectorCommandName.PUT;
import static com.cleo.connector.api.command.ConnectorCommandOption.Delete;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.cleo.connector.api.ConnectorClient;
import com.cleo.connector.api.ConnectorException;
import com.cleo.connector.api.annotations.Command;
import com.cleo.connector.api.annotations.UnderlyingPath;
import com.cleo.connector.api.command.ConnectorCommandResult;
import com.cleo.connector.api.command.ConnectorCommandResult.Status;
import com.cleo.connector.api.command.DirCommand;
import com.cleo.connector.api.command.GetCommand;
import com.cleo.connector.api.command.PutCommand;
import com.cleo.connector.api.directory.Directory.Type;
import com.cleo.connector.api.directory.Entry;
import com.cleo.connector.api.helper.Attributes;
import com.cleo.connector.api.interfaces.IConnectorIncoming;
import com.cleo.connector.api.interfaces.IConnectorOutgoing;
import com.cleo.connector.file.FileAttributes;
import com.cleo.labs.util.zip.ZipDirectoryInputStream;
import com.cleo.labs.util.zip.ZipDirectoryOutputStream;
import com.google.common.base.Strings;

public class ZipConnectorClient extends ConnectorClient {
    private ZipConnectorConfig config;

    /**
     * Constructs a new {@code ZipConnectorClient} for the schema
     * @param schema the {@code ZipConnectorSchema}
     */
    public ZipConnectorClient(ZipConnectorSchema schema) {
        this.config = new ZipConnectorConfig(this, schema);
    }

    private static final String DIRECTORY_ZIP = "directory.zip";
    private Entry directoryZip(String directory) {
        Entry entry = new Entry(Type.file);
        entry.setPath(Paths.get(directory, DIRECTORY_ZIP).toString());
        entry.setDate(Attributes.toLocalDateTime(System.currentTimeMillis()));
        entry.setSize(-1L);
        return entry;
    }

    @Command(name=DIR)
    public ConnectorCommandResult dir(DirCommand dir) throws ConnectorException, IOException {
        logger.debug(String.format("DIR '%s'", dir.getSource().getPath()));
        String rootPath = config.getRootPath();
        Path path = Paths.get(rootPath, dir.getSource().getPath());
        blockAccessOutsideRootPath(path, rootPath);
        path = validatePath(path);
        if (!path.toFile().exists()) {
            throw new ConnectorException(String.format("'%s' does not exist or is not accessible", path.toString()),
                    ConnectorException.Category.fileNonExistentOrNoAccess);
        }

        List<Entry> dirList = new ArrayList<>();

        File[] files = path.toFile().listFiles();
        if (files != null) {
            for (File file: files) {
                if (file.isDirectory()) {
                    Entry entry = new Entry(Type.dir);
                    String childpath = Paths.get(dir.getSource().getPath(), file.getName()).toString();
                    entry.setPath(childpath);
                    entry.setDate(Attributes.toLocalDateTime(file.lastModified()));
                    entry.setSize(-1L);
                    dirList.add(entry);
                }
            }
        }
        dirList.add(directoryZip(dir.getSource().getPath()));
        return new ConnectorCommandResult(Status.Success, Optional.empty(), dirList);
    }

    @Command(name = GET)
    public ConnectorCommandResult get(GetCommand get) throws ConnectorException, IOException {
        String source = get.getSource().getPath();
        IConnectorIncoming destination = get.getDestination();

        logger.debug(String.format("GET remote '%s' to local '%s'", source, destination.getPath()));

        Path path = Paths.get(config.getRootPath(), source);
        blockAccessOutsideRootPath(path, config.getRootPath());
        path = validatePath(path);
        try (ZipDirectoryInputStream zip = new ZipDirectoryInputStream(path.getParent(), config.getCompressionLevel())) {
            transfer(zip, destination.getStream(), true);
            return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
        } catch (IOException ioe) {
            throw new ConnectorException(String.format("'%s' does not exist or is not accessible", source),
                ioe, ConnectorException.Category.fileNonExistentOrNoAccess);
        }
    }

    @Command(name = PUT, options = { Delete })
    public ConnectorCommandResult put(PutCommand put) throws ConnectorException, IOException {
        String destination = put.getDestination().getPath();
        IConnectorOutgoing source = put.getSource();

        logger.debug(String.format("PUT local '%s' to remote '%s'", source.getPath(), destination));

        Path path = Paths.get(config.getRootPath(), destination);
        blockAccessOutsideRootPath(path, config.getRootPath());
        path = validatePath(path);

        try (ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream(path.getParent())) {
            if (config.getSimulateUnzip()) {
                unzip.setProcessor((e, ef) -> {
                    if (e.isDirectory()) {
                        logger.logDetail("mkdir "+e.getName(), 1);
                    } else {
                        logger.logDetail("file "+e.getName(), 1);
                    }
                    return null;
                });
            }
            transfer(source.getStream(), unzip, false);
            logger.debug(("unzip complete. buffer length="+unzip.getBufferLength()));
            return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
        } catch (IOException ioe) {
            throw new ConnectorException(String.format("error unzipping '%s'", source),
                ioe, ConnectorException.Category.fileNonExistentOrNoAccess);
        }
    }

    /**
     * Get the file attribute view associated with a file path
     * 
     * @param path the file path
     * @return the file attributes
     * @throws com.cleo.connector.api.ConnectorException
     * @throws java.io.IOException
     */
    @Command(name = ATTR)
    public BasicFileAttributeView getAttributes(String path) throws ConnectorException, IOException {
        logger.debug(String.format("ATTR '%s'", path));
        try {
            String rootPath = config.getRootPath();
            Path fullPath = Paths.get(rootPath, path);
            blockAccessOutsideRootPath(fullPath, rootPath);
            fullPath = validatePath(fullPath);
            File file = fullPath.toFile();
            if (file.getName().equals(DIRECTORY_ZIP)) {
                return new ZipFileAttributes(DIRECTORY_ZIP,
                        new ZipDirectoryInputStream(fullPath.getParent(), config.getCompressionLevel()),
                        logger);
            } else if (!file.exists() || !file.isDirectory()) {
                throw new ConnectorException(String.format("'%s' does not exist or is not accessible", path),
                        ConnectorException.Category.fileNonExistentOrNoAccess);
            }
            return new FileAttributes(file);
        } catch (InvalidPathException e) {
            throw new ConnectorException(String.format("'%s' does not exist or is not accessible", path),
                    ConnectorException.Category.fileNonExistentOrNoAccess);
        }
    }

    /**
     * If no "Root Path" is configured, then the path must be absolute.
     * @param path Resolved path to check
     * @throws ConnectorException 
     */
    private Path validatePath(Path path) throws ConnectorException {
        String rootPath = config.getRootPath();
        Path retPath = path;
        if (Strings.isNullOrEmpty(rootPath)) {
            if (!path.isAbsolute()) {
                Path tmpPath = path;
                String pathStr = path.toString();
                if (((!pathStr.startsWith("/")) && (!pathStr.startsWith("\\"))) || !pathStr.matches("[A-Za-z]:")) {
                    tmpPath = Paths.get(File.separator + path.toString());
                }
                if (!tmpPath.isAbsolute()) {
                    throw new ConnectorException(String.format("'%s' must be an absolute path for this URI. If this is a UNC path, a System Scheme Name must be used.", path));
                }
                retPath = tmpPath;
            }
        }
        return retPath;
    }

    /**
     * Returns the underlying/actual path to the file. This is used for checking
     * Authorized Roots. The @UnderlyingPath attribute signifies this is the method
     * to call to get the underlying path.
     * @param path Path from the URI for which to get the underlying path
     * @return The actual path based on the configured root path
     * @throws ConnectorException 
     * @throws java.io.IOException 
     */
    @UnderlyingPath
    public String getUnderlyingPath(String path) throws ConnectorException, IOException {
        // Get the configured root path
        String rootPath = config.getRootPath();
        // Build the full path based on the root path
        Path fullPath = Paths.get(rootPath, path);
        // Return the path as a String
        return fullPath.toFile().getPath();
    }

    public boolean blockAccessOutsideRootPath(Path path, String rootPathValue) throws ConnectorException, IOException {
        if (!Strings.isNullOrEmpty(rootPathValue)) {
            rootPathValue = rootPathValue.replaceAll("/*$", "");
            if (!path.normalize().startsWith(Paths.get(rootPathValue).normalize())) {
                throw new ConnectorException(String.format("'%s' is not accessible.", path.toString()));
            }
        }
        return true;
    }
}
