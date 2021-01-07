package com.cleo.labs.connector.zip;

import static com.cleo.connector.api.command.ConnectorCommandName.ATTR;
import static com.cleo.connector.api.command.ConnectorCommandName.DIR;
import static com.cleo.connector.api.command.ConnectorCommandName.GET;
import static com.cleo.connector.api.command.ConnectorCommandName.PUT;
import static com.cleo.connector.api.command.ConnectorCommandOption.Delete;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cleo.connector.api.ConnectorClient;
import com.cleo.connector.api.ConnectorException;
import com.cleo.connector.api.annotations.Command;
import com.cleo.connector.api.annotations.UnderlyingPath;
import com.cleo.connector.api.command.ConnectorCommandResult;
import com.cleo.connector.api.command.ConnectorCommandResult.Status;
import com.cleo.connector.api.command.DirCommand;
import com.cleo.connector.api.command.GetCommand;
import com.cleo.connector.api.command.PutCommand;
import com.cleo.connector.api.directory.Entry;
import com.cleo.connector.api.interfaces.IConnectorConfig;
import com.cleo.connector.api.interfaces.IConnectorIncoming;
import com.cleo.connector.api.interfaces.IConnectorOutgoing;
import com.cleo.connector.shell.interfaces.IConnector;
import com.cleo.connector.shell.interfaces.IConnectorHost;
import com.cleo.labs.connector.zip.ZipConnectorSchema.UnzipMode;
import com.cleo.labs.util.zip.Finder;
import com.cleo.labs.util.zip.LocalFinderInputStream;
import com.cleo.labs.util.zip.PartitionedZipDirectory;
import com.cleo.labs.util.zip.PartitionedZipDirectory.Partition;
import com.cleo.labs.util.zip.ZipDirectoryInputStream;
import com.cleo.labs.util.zip.ZipDirectoryOutputStream;
import com.cleo.labs.util.zip.ZipDirectoryOutputStream.UnZipEntry;
import com.cleo.util.MacroUtil;
import com.google.common.base.Strings;

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
        return dir.replaceFirst("(?<![/\\\\])$", "/");
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
        return file.replaceFirst("(?:^|(?<=[/\\\\]))[^/\\\\]+[/\\\\]?$", "");
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

        PartitionedZipDirectory zip = PartitionedZipDirectory.builder(factory.getFile(root+source))
                .opener(f -> factory.getInputStream(f.file(), MacroUtil.SOURCE_FILE))
                .level(config.getCompressionLevel())
                .filter(Finder.excluding(config.getExclusions()))
                .directoryMode(config.getDirectoryMode())
                .threshold(config.getZipSizeThreshold())
                .build();
        List<Partition> partitions = zip.partitions();
        ZipFilenameEncoder encoder = new ZipFilenameEncoder(partitions);
        List<Entry> dirList = encoder.getEntries(source);
        return new ConnectorCommandResult(Status.Success, Optional.empty(), dirList);
    }

    public static final String DIRECTORY_LISTING = "directory.listing";

    private InputStream getRemoteReplicaInputStream() throws IOException {
        String remoteReplica = config.getRemoteDirectoryListing();
        if (!Strings.isNullOrEmpty(remoteReplica)) {
            logger.debug("comparing files to "+remoteReplica);
            return factory.getInputStream(remoteReplica, MacroUtil.SOURCE_FILE);
        }
        return null;
    }

    @Command(name = GET)
    public ConnectorCommandResult get(GetCommand get) throws ConnectorException, IOException {
        IConnectorIncoming destination = get.getDestination();
        String root = asDirectory(config.getRootPath());
        String sourceDir = justDirectory(get.getSource().getPath());
        String sourceFile = stripRoot(get.getSource().getPath());

        logger.debug(String.format("GET remote '%s' to local '%s'", sourceFile, destination.getPath()));

        factory.setSourceAndDest(get.getSource().getPath(), get.getDestination().getName(), MacroUtil.SOURCE_FILE, logger);

        File file = factory.getFile(root+sourceFile);
        ZipFilenameEncoder encoder = new ZipFilenameEncoder();
        Partition partition;

        if (file.getName().equals(DIRECTORY_LISTING)) {
            try (LocalFinderInputStream in = LocalFinderInputStream.builder(factory.getFile(root+sourceDir))
                    .filter(Finder.excluding(config.getExclusions()))
                    .debug(s -> logger.debug(s))
                    .build()) {
                transfer(in, destination.getStream(), true);
                return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
            } catch (IOException ioe) {
                throw new ConnectorException(String.format("'%s' does not exist or is not accessible", sourceFile),
                    ioe, ConnectorException.Category.fileNonExistentOrNoAccess);
            }
        } else if ((partition = encoder.parseFilename(file.getName())) != null) {
            try (ZipDirectoryInputStream zip = ZipDirectoryInputStream.builder(factory.getFile(root+sourceDir))
                    .opener(f -> factory.getInputStream(f.file(), MacroUtil.SOURCE_FILE))
                    .level(config.getCompressionLevel())
                    .filter(Finder.excluding(config.getExclusions())
                            .and(Finder.only(config.getSelect())))
                    .directoryMode(config.getDirectoryMode())
                    .restart(partition.checkpoint())
                    .limit(partition.count())
                    .build()) {
                transfer(zip, destination.getStream(), true);
                return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
            } catch (IOException ioe) {
                throw new ConnectorException(String.format("'%s' does not exist or is not accessible", sourceFile),
                    ioe, ConnectorException.Category.fileNonExistentOrNoAccess);
            }
        } else {
            try (ZipDirectoryInputStream zip = ZipDirectoryInputStream.builder(factory.getFile(root+sourceDir))
                    .opener(f -> factory.getInputStream(f.file(), MacroUtil.SOURCE_FILE))
                    .level(config.getCompressionLevel())
                    .filter(Finder.excluding(config.getExclusions())
                            .and(Finder.only(config.getSelect())))
                    .directoryMode(config.getDirectoryMode())
                    .remoteReplica(getRemoteReplicaInputStream())
                    .debug(s -> logger.debug(s))
                    .timeout(config.getRemoteDirectoryListingTimeout(), config.getRemoteDirectoryListingTimeoutUnit())
                    .build()) {
                transfer(zip, destination.getStream(), true);
                return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
            } catch (IOException ioe) {
                throw new ConnectorException(String.format("'%s' does not exist or is not accessible", sourceFile),
                    ioe, ConnectorException.Category.fileNonExistentOrNoAccess);
            }
        }
    }

    private class UnZipProcessor implements ZipDirectoryOutputStream.UnZipProcessor {
        private String root;
        private String tempdir = null;
        private ZipDirectoryOutputStream.UnZipProcessor logProcessor = null;
        public UnZipProcessor(String root) {
            this.root = root;
            if (config.getUnzipMode()==UnzipMode.unzipAndLog) {
                logProcessor = getUnZipLogger();
            }
        }
        @Override
        public OutputStream process(UnZipEntry zip) throws IOException {
            if (logProcessor != null) {
                logProcessor.process(zip);
            }
            if (zip.entry().isDirectory()) {
                if (!config.getSuppressDirectoryCreation()) {
                    zip.file().mkdirs();
                }
                return null;
            } else if (config.unzipRootFilesLast() && zip.path().getNameCount() == 1) {
                return factory.getOutputStream(saveForLast(zip.file()), zip.entry().getTime());
            } else {
                if (!config.getSuppressDirectoryCreation()) {
                    File parent = zip.file().getParentFile();
                    if (!parent.exists()) {
                        parent.mkdirs();
                    } else if (!parent.isDirectory()) {
                        throw new IOException("can not create parent directory for "+zip.entry().getName()+": file already exists");
                    }
                }
                return factory.getOutputStream(zip.file(), zip.entry().getTime());
            }
        }
        public File saveForLast(File file) throws IOException {
            if (tempdir == null) {
                for (int tries=0; tempdir==null && tries<10; tries++) {
                    String test = root+"/"+Paths.get("."+UUID.randomUUID().toString());
                    File testfile = factory.getFile(test);
                    if (!testfile.exists() && testfile.mkdir()) {
                        tempdir = test;
                    }
                }
                if (tempdir == null) {
                    throw new IOException("failed to create holding directory for Unzip Root Files Last option");
                }
            }
            File temp = factory.getFile(tempdir, Paths.get(file.getName()));
            logger.debug("saving "+temp.getPath());
            return temp;
        }
        public void finish() throws IOException {
            if (tempdir != null) {
                File tempdirfile = factory.getFile(tempdir);
                for (File temp : tempdirfile.listFiles()) {
                    File target = factory.getFile(root, Paths.get(temp.getName()));
                    temp.renameTo(target);
                    logger.debug("restoring "+temp.getName()+" to "+target.getPath());
                }
                if (tempdirfile.list().length != 0) {
                    throw new IOException("failed to rename all files from holding directory: "+tempdir);
                }
                tempdirfile.delete();
            }
        }
    }

    private ZipDirectoryOutputStream.UnZipProcessor getUnZipLogger() {
        return zip -> {
            if (zip.entry().isDirectory()) {
                if (!config.getSuppressDirectoryCreation()) {
                    logger.logDetail("mkdir "+zip.entry().getName(), 1);
                }
            } else {
                logger.logDetail("file "+zip.entry().getName(), 1);
            }
            return null;
        };
    }

    @Command(name = PUT, options = { Delete })
    public ConnectorCommandResult put(PutCommand put) throws ConnectorException, IOException {
        IConnectorOutgoing source = put.getSource();
        String root = asDirectory(config.getRootPath());
        String destination = justDirectory(put.getDestination().getPath());

        logger.debug(String.format("PUT local '%s' to remote '%s'", source.getPath(), put.getDestination().getPath()));

        factory.setSourceAndDest(put.getSource().getName(), put.getDestination().getPath(), MacroUtil.DEST_FILE, logger);

        UnZipProcessor processor = null;

        try (ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream(p -> factory.getFile(root+destination, p))) {
            unzip.setFilter(ZipDirectoryOutputStream.excluding(config.getExclusions()));
            switch (config.getUnzipMode()) {
            case unzip:
            case unzipAndLog:
                processor = this.new UnZipProcessor(root+destination);
                unzip.setProcessor(processor);
                break;
            case log:
                unzip.setProcessor(getUnZipLogger());
                break;
            case preflight:
                unzip.setProcessor(zip -> {
                    // first check the implied parent directory unless we're at the top of the zip
                    if (zip.path().getNameCount() > 1) {
                        File parent = zip.file().getParentFile();
                        if (parent.exists()) {
                            if (!parent.isDirectory()) {
                                // if we need a directory where there is an existing file, this is a problem no matter what
                                throw new IOException("conflict detected for directory "+parent.getPath()+": file already exists");
                            } else if (!config.getSuppressDirectoryCreation()) {
                                // if the directory is already there, suppress failure under SuppressDirectoryCreation
                                throw new IOException("conflict detected for directory "+parent.getPath()+": directory already exists");
                            }
                        }
                    }
                    // now check the file/directory itself
                    if (zip.file().exists()) {
                        if (!zip.file().isDirectory()) {
                            // conflicts for files always fail
                            throw new IOException("conflict detected for file "+zip.file().getPath()+": file already exists");
                        } else if (!zip.file().isDirectory()) {
                            // if we need a directory where there is an existing file, this is a problem no matter what
                            throw new IOException("conflict detected for directory "+zip.file().getPath()+": file already exists");
                        } else if (!config.getSuppressDirectoryCreation()) {
                            // if the directory is already there, suppress failure under SuppressDirectoryCreation
                            throw new IOException("conflict detected for directory "+zip.file().getPath()+": directory already exists");
                        }
                    }
                    return null;
                });
                break;
            default:
                // won't happen
                break;
            }
            transfer(source.getStream(), unzip, false);
            if (processor != null) {
                processor.finish();
            }
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
        String sourceDir = justDirectory(path);
        String sourceFile = stripRoot(path);

        factory.setSourceAndDest(path, null, MacroUtil.SOURCE_FILE, logger);
        File file = factory.getFile(root+sourceFile);
        ZipFilenameEncoder encoder = new ZipFilenameEncoder();
        Partition partition = encoder.parseFilename(file.getName());

        if (partition != null) {
            return new ZipFileAttributes(file.getName(), partition);
        } else {
            File dir = factory.getFile(root+sourceDir);
            if (dir.exists() && dir.isDirectory()) {
                return new ZipFileAttributes(file.getName(), null);
            }
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
