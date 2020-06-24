package com.cleo.labs.connector.zip;

import static com.cleo.connector.api.command.ConnectorCommandName.ATTR;
import static com.cleo.connector.api.command.ConnectorCommandName.DIR;
import static com.cleo.connector.api.command.ConnectorCommandName.GET;
import static com.cleo.connector.api.command.ConnectorCommandName.PUT;
import static com.cleo.connector.api.command.ConnectorCommandOption.Delete;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
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
import com.cleo.connector.api.interfaces.IConnectorConfig;
import com.cleo.connector.api.interfaces.IConnectorIncoming;
import com.cleo.connector.api.interfaces.IConnectorOutgoing;
import com.cleo.connector.file.FileAttributes;
import com.cleo.connector.shell.interfaces.IConnector;
import com.cleo.connector.shell.interfaces.IConnectorHost;
import com.cleo.labs.util.zip.ZipDirectoryInputStream;
import com.cleo.labs.util.zip.ZipDirectoryOutputStream;
import com.cleo.util.MacroUtil;

public class ZipConnectorClient extends ConnectorClient {
    private ZipConnectorConfig config;
    private LexFileFactory factory;

    /**
     * Constructs a new {@code ZipConnectorClient} for the schema
     * @param schema the {@code ZipConnectorSchema}
     */
    public ZipConnectorClient(ZipConnectorSchema schema) {
        this.config = new ZipConnectorConfig(this, schema);
        this.factory = new LexFileFactory();
    }

    @Override
    public ConnectorClient setup(IConnector connector, IConnectorConfig connectorConfig, IConnectorHost connectorHost) {
        super.setup(connector, connectorConfig, connectorHost);
        factory.setup(connectorHost, this.getAction());
        return this;
    }

    /**
     * Converts \ to / and makes sure there is a trailing /.
     * @param dir a possibly messed up directory name
     * @return a cleaned up directory name
     */
    private String asDirectory(String dir) {
        return dir.replace('\\', '/').replaceFirst("(?<!/)$", "/");
    }
    /**
     * Strips off any leading root indicator: a drive-letter: and/or leading \ or /.
     * @param dir a possibly messed up directory name
     * @return a cleaned up directory name
     */
    private String stripRoot(String dir) {
        return dir.replaceFirst("^(?:\\p{Alpha}:)?[\\\\/]?", "");
    }
    /**
     * Returns the directory prefix of a file path, if any.
     * Converts \ to /, trims off any trailing /, then removes
     * the final /-separated element.
     * @param file a possibly messed up file path
     * @return the (possibly empty, but if not ending in /) path prefix
     */
    private String justDirectory(String file) {
        return file.replace('\\', '/').replaceFirst("(?:^|(?<=/))[^/]+/?$", "");
    }

    private static final String DIRECTORY_ZIP = "directory.zip";
    private Entry directoryZip(String directory) {
        Entry entry = new Entry(Type.file);
        entry.setPath(directory + DIRECTORY_ZIP);
        entry.setDate(Attributes.toLocalDateTime(System.currentTimeMillis()));
        entry.setSize(-1L);
        return entry;
    }

    @Command(name=DIR)
    public ConnectorCommandResult dir(DirCommand dir) throws ConnectorException, IOException {
        String root = asDirectory(config.getRootPath());
        String source = asDirectory(stripRoot(dir.getSource().getPath()));
        logger.debug(String.format("DIR '%s'", source));

        File file = factory.getFile(root+source);
        if (!file.exists()) {
            throw new ConnectorException(String.format("'%s' does not exist or is not accessible", source),
                    ConnectorException.Category.fileNonExistentOrNoAccess);
        }
        factory.setSourceAndDest(source, null, MacroUtil.SOURCE_FILE, logger);

        List<Entry> dirList = new ArrayList<>(1);
        dirList.add(directoryZip(source));
        return new ConnectorCommandResult(Status.Success, Optional.empty(), dirList);
    }

    @Command(name = GET)
    public ConnectorCommandResult get(GetCommand get) throws ConnectorException, IOException {
        IConnectorIncoming destination = get.getDestination();
        String root = asDirectory(config.getRootPath());
        String source = justDirectory(get.getSource().getPath());

        logger.debug(String.format("GET remote '%s' to local '%s'", source, destination.getPath()));

        factory.setSourceAndDest(get.getSource().getPath(), get.getDestination().getName(), MacroUtil.SOURCE_FILE, logger);

        try (ZipDirectoryInputStream zip = new ZipDirectoryInputStream(factory.getFile(root+source), config.getCompressionLevel())) {
            transfer(zip, destination.getStream(), true);
            return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
        } catch (IOException ioe) {
            throw new ConnectorException(String.format("'%s' does not exist or is not accessible", source),
                ioe, ConnectorException.Category.fileNonExistentOrNoAccess);
        }
    }

    @Command(name = PUT, options = { Delete })
    public ConnectorCommandResult put(PutCommand put) throws ConnectorException, IOException {
        IConnectorOutgoing source = put.getSource();
        String root = asDirectory(config.getRootPath());
        String destination = justDirectory(put.getDestination().getPath());

        logger.debug(String.format("PUT local '%s' to remote '%s'", source.getPath(), put.getDestination().getPath()));

        factory.setSourceAndDest(put.getSource().getName(), put.getDestination().getPath(), MacroUtil.DEST_FILE, logger);

        try (ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream(p -> factory.getFile(root+destination, p))) {
            if (config.getSimulateUnzip()) {
                unzip.setProcessor((e, ef) -> {
                    if (e.isDirectory()) {
                        logger.logDetail("mkdir "+e.getName(), 1);
                    } else {
                        logger.logDetail("file "+e.getName(), 1);
                    }
                    return null;
                });
            } else {
                unzip.setProcessor((e, ef) -> {
                    if (e.isDirectory()) {
                        ef.mkdirs();
                        return null;
                    } else {
                        File parent = ef.getParentFile();
                        if (!parent.exists()) {
                            parent.mkdirs();
                        } else if (!parent.isDirectory()) {
                            throw new IOException("can not create parent directory for "+e.getName()+": file already exists");
                        }
                        return factory.getOutputStream(ef, MacroUtil.DEST_FILE);
                    }
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
        String root = asDirectory(config.getRootPath());
        String filename = root + stripRoot(path);
        factory.setSourceAndDest(path, null, MacroUtil.SOURCE_FILE, logger);
        File file = factory.getFile(filename);
        if (file.getName().equals(DIRECTORY_ZIP)) {
            try {
                ZipDirectoryInputStream zis = new ZipDirectoryInputStream(factory.getFile(root+justDirectory(path)), config.getCompressionLevel());
                return new ZipFileAttributes(DIRECTORY_ZIP, zis, logger);
            } catch (NoSuchFileException e) {
                // fall through to fileNonExistentOrNoAccess
            }
        } else if (file.exists() && file.isDirectory()) {
            return new FileAttributes(file);
        }
        throw new ConnectorException(String.format("'%s' does not exist or is not accessible", path),
                ConnectorException.Category.fileNonExistentOrNoAccess);
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
        return asDirectory(config.getRootPath()+stripRoot(path));
    }

}
