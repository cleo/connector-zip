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
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
import com.cleo.labs.util.zip.Found;
import com.cleo.labs.util.zip.LocalFinderInputStream;
import com.cleo.labs.util.zip.PartitionedZipDirectory;
import com.cleo.labs.util.zip.PartitionedZipDirectory.Partition;
import com.cleo.labs.util.zip.PathUtil;
import com.cleo.labs.util.zip.ThreadedZipDirectoryInputStream;
import com.cleo.labs.util.zip.UnzipDirectoryStreamWrapper;
import com.cleo.labs.util.zip.UnzipProcessor;
import com.cleo.labs.util.zip.ZipDirectoryInputStream;
import com.cleo.labs.util.zip.Finder.DirectoryMode;
import com.cleo.labs.util.zip.Found.Operation;
import com.cleo.util.MacroUtil;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;

public class ZipConnectorClient extends ConnectorClient {
    private ZipConnectorConfig config;
    private FileFactory factory;

    /**
     * Constructs a new {@code ZipConnectorClient} for the schema
     * @param schema the {@code ZipConnectorSchema}
     */
    public ZipConnectorClient(ZipConnectorSchema schema) {
        this.config = new ZipConnectorConfig(this, schema);
        this.factory = new LexFileFactory();
    }

    /**
     * Constructs a new {@code ZipConnectorClient} for the schema
     * and an explicit FileFactory (for testing)
     * @param schema the {@code ZipConnectorSchema}
     * @param factory the {@code FileFactory} to use (for testing)
     */
    public ZipConnectorClient(ZipConnectorSchema schema, FileFactory factory) {
        this.config = new ZipConnectorConfig(this, schema);
        this.factory = factory;
    }

    @Override
    public ConnectorClient setup(IConnector connector, IConnectorConfig connectorConfig, IConnectorHost connectorHost) {
        super.setup(connector, connectorConfig, connectorHost);
        factory.setup(connectorHost, this.getAction());
        return this;
    }

    @Command(name=DIR)
    public ConnectorCommandResult dir(DirCommand dir) throws ConnectorException, IOException {
        String root = PathUtil.asDirectory(config.getRootPath());
        String source = PathUtil.asDirectory(PathUtil.stripRoot(dir.getSource().getPath()));
        logger.debug(String.format("DIR '%s'", source));

        File file = factory.getFile(root+source);
        if (!file.exists()) {
            throw new ConnectorException(String.format("'%s' does not exist or is not accessible", source),
                    ConnectorException.Category.fileNonExistentOrNoAccess);
        }
        factory.setSourceAndDest(source, null, MacroUtil.SOURCE_FILE, s -> logger.debug(s));

        PartitionedZipDirectory zip = PartitionedZipDirectory.builder(factory.getFile(root+source))
                .opener(f -> factory.getInputStream(f.file()))
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
            return factory.getInputStream(remoteReplica);
        }
        return null;
    }

    @Command(name = GET)
    public ConnectorCommandResult get(GetCommand get) throws ConnectorException, IOException {
        IConnectorIncoming destination = get.getDestination();
        String root = PathUtil.asDirectory(config.getRootPath());
        String sourceDir = PathUtil.justDirectory(get.getSource().getPath());
        String sourceFile = PathUtil.stripRoot(get.getSource().getPath());

        logger.debug(String.format("GET remote '%s' to local '%s'", sourceFile, destination.getPath()));

        factory.setSourceAndDest(get.getSource().getPath(), get.getDestination().getName(), MacroUtil.SOURCE_FILE, s -> logger.debug(s));

        File file = factory.getFile(root+sourceFile);
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
        } else if ((partition = ZipFilenameEncoder.parseFilename(file.getName())) != null) {
            try (ZipDirectoryInputStream zip = ZipDirectoryInputStream.builder(factory.getFile(root+sourceDir))
                    .opener(f -> factory.getInputStream(f.file()))
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
            try (ThreadedZipDirectoryInputStream zip = ThreadedZipDirectoryInputStream.builder(factory.getFile(root+sourceDir))
                    .copier(factory.getCopier())
                    .level(config.getCompressionLevel())
                    .filter(Finder.excluding(config.getExclusions())
                            .and(Finder.only(config.getSelect())))
                    .directoryMode(config.getDirectoryMode())
                    .remoteReplica(getRemoteReplicaInputStream())
                    .debug(s -> logger.debug(s))
                    .timeout(config.getRemoteDirectoryListingTimeout(), config.getRemoteDirectoryListingTimeoutUnit())
                    .build()) {
                zip.finder().replicateDeletes(config.getReplicateDeletes());
                transfer(zip, destination.getStream(), true);
                return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
            } catch (IOException ioe) {
                throw new ConnectorException(String.format("'%s' does not exist or is not accessible", sourceFile),
                    ioe, ConnectorException.Category.fileNonExistentOrNoAccess);
            }
        }
    }

    private class Unzipper implements UnzipProcessor {
        private String root;
        private String tempdir = null;
        private UnzipProcessor logProcessor = null;
        private ExecutorService pool = Executors.newCachedThreadPool();
        private IOException exception = null;
        public Unzipper(String root) {
            this.root = root;
            if (config.getUnzipMode()==UnzipMode.unzipAndLog) {
                logProcessor = getUnzipLogger();
            }
        }
        private boolean rmdirs(File root) {
            for (Found file : new Finder(root).directoryMode(DirectoryMode.exclude)) {
                if (!file.file().delete() && file.file().exists()) {
                    return false;
                }
            }
            List<Found> dirs =
                    Lists.reverse(Lists.newArrayList(
                            new Finder(root).directoryMode(DirectoryMode.only).iterator()));
            for (Found dir : dirs) {
                if (!dir.file().delete() && dir.file().exists()) {
                    return false;
                }
            }
            return true;
        }
        @Override
        public OutputStream process(Found zip) throws IOException {
            if (exception != null) {
                throw exception;
            }
            if (zip.directory()) {
                pool.execute(() -> {
                    try {
                        if (logProcessor != null) {
                            logProcessor.process(zip);
                        }
                        if (zip.operation()==Operation.add) { 
                            if (!config.getSuppressDirectoryCreation()) {
                                zip.file().mkdirs();
                            }
                        } else if (zip.operation()==Operation.delete) {
                            if (config.getReplicateDeletes() && !rmdirs(zip.file())) {
                                logger.logError("unable to delete directory "+zip.fullname());
                            }
                        }
                    } catch (IOException e) {
                        exception = e;
                    }
                });
                return null;
            } else {
                PipedOutputStream out = new PipedOutputStream();
                PipedInputStream in = new PipedInputStream(out, ThreadedZipDirectoryInputStream.DEFAULT_BUFFERSIZE);
                pool.execute(() -> {
                    OutputStream file = null;
                    try {
                        if (logProcessor != null) {
                            logProcessor.process(zip);
                        }
                        if (zip.operation()==Operation.add) {
                            if (config.unzipRootFilesLast() && zip.path().length == 1) {
                                file = factory.getOutputStream(saveForLast(zip.file()), zip.modified());
                            } else {
                                if (!config.getSuppressDirectoryCreation()) {
                                    File parent = zip.file().getParentFile();
                                    if (!parent.exists()) {
                                        parent.mkdirs();
                                    } else if (!parent.isDirectory()) {
                                        throw new IOException("can not create parent directory for "+zip.fullname()+": file already exists");
                                    }
                                }
                                file = factory.getOutputStream(zip.file(), zip.modified());
                            }
                            ByteStreams.copy(in, file);
                        } else if (zip.operation()==Operation.delete) {
                            if (config.getReplicateDeletes() && !zip.file().delete()) {
                                logger.logError("unable to delete file "+zip.fullname());
                            }
                        }
                    } catch (IOException e) {
                        exception = e;
                    } finally {
                        if (file != null) {
                            try {
                                file.close();
                            } catch (IOException e) {
                            }
                        }
                        try {
                            in.close();
                        } catch (IOException e) {
                        }
                    }
                });
                return out;
            }
        }
        public File saveForLast(File file) throws IOException {
            if (tempdir == null) {
                for (int tries=0; tempdir==null && tries<10; tries++) {
                    String test = root+"/."+UUID.randomUUID().toString();
                    File testfile = factory.getFile(test);
                    if (!testfile.exists() && testfile.mkdir()) {
                        tempdir = test;
                    }
                }
                if (tempdir == null) {
                    throw new IOException("failed to create holding directory for Unzip Root Files Last option");
                }
            }
            File temp = factory.getFile(tempdir, new String[] {file.getName()});
            logger.debug("saving "+temp.getPath());
            return temp;
        }
        public void finish() throws IOException {
            shutdownAndAwaitTermination();
            if (tempdir != null) {
                File tempdirfile = factory.getFile(tempdir);
                for (File temp : tempdirfile.listFiles()) {
                    File target = factory.getFile(root, new String[] {temp.getName()});
                    temp.renameTo(target);
                    logger.debug("restoring "+temp.getName()+" to "+target.getPath());
                }
                if (tempdirfile.list().length != 0) {
                    throw new IOException("failed to rename all files from holding directory: "+tempdir);
                }
                tempdirfile.delete();
            }
            if (exception != null) {
                throw exception;
            }
        }
        private void shutdownAndAwaitTermination() throws IOException {
            pool.shutdown(); // Disable new tasks from being submitted
            try {
                // Wait a while for existing tasks to terminate
                if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    pool.shutdownNow(); // Cancel currently executing tasks
                    // Wait a while for tasks to respond to being cancelled
                    if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                        throw new IOException("unzip file writing thread pool did not terminate");
                    }
                }
            } catch (InterruptedException ie) {
                // (Re-)Cancel if current thread also interrupted
                pool.shutdownNow();
                // Preserve interrupt status
                Thread.currentThread().interrupt();
            }
        }
    }

    private UnzipProcessor getUnzipLogger() {
        return zip -> {
            if (zip.directory()) {
                if (zip.operation()==Operation.add) { 
                    if (!config.getSuppressDirectoryCreation()) {
                        logger.logDetail("mkdir "+zip.fullname(), 1);
                    }
                } else if (zip.operation()==Operation.delete) {
                    logger.logDetail("rmdir "+zip.fullname(), 1);
                }
            } else {
                if (zip.operation()==Operation.add) { 
                    logger.logDetail("create "+zip.fullname(), 1);
                } else if (zip.operation()==Operation.delete) {
                    logger.logDetail("delete "+zip.fullname(), 1);
                }
            }
            return null;
        };
    }

    @Command(name = PUT, options = { Delete })
    public ConnectorCommandResult put(PutCommand put) throws ConnectorException, IOException {
        IConnectorOutgoing source = put.getSource();
        String root = PathUtil.asDirectory(config.getRootPath());
        String destination = PathUtil.justDirectory(put.getDestination().getPath());

        logger.debug(String.format("PUT local '%s' to remote '%s'", source.getPath(), put.getDestination().getPath()));

        factory.setSourceAndDest(put.getSource().getName(), put.getDestination().getPath(), MacroUtil.DEST_FILE, s -> logger.debug(s));

        Unzipper processor = null;

        try (UnzipDirectoryStreamWrapper unzip = new UnzipDirectoryStreamWrapper(p -> factory.getFile(root+destination, p))) {
            unzip.filter(Finder.excluding(config.getExclusions()))
                 .interrupted(() -> connectorAction.isInterrupted());
            switch (config.getUnzipMode()) {
            case unzip:
            case unzipAndLog:
                processor = this.new Unzipper(root+destination);
                unzip.processor(processor);
                break;
            case log:
                unzip.processor(getUnzipLogger());
                break;
            case preflight:
                unzip.processor(zip -> {
                    // first check the implied parent directory unless we're at the top of the zip
                    if (zip.path().length > 1) {
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
            unzip.process(source.getStream(), PathUtil.justFile(put.getDestination().getPath(), "file.bin"));
            // transfer(source.getStream(), unzip, false);
            if (processor != null) {
                processor.finish();
                processor = null;
            }
            logger.debug("unzip complete.");
            return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
        } catch (IOException ioe) {
            if (processor != null) {
                try {
                    processor.finish();
                } catch (IOException ignore) {
                }
            }
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
        String root = PathUtil.asDirectory(config.getRootPath());
        String sourceDir = PathUtil.justDirectory(path);
        String sourceFile = PathUtil.stripRoot(path);

        factory.setSourceAndDest(path, null, MacroUtil.SOURCE_FILE, s -> logger.debug(s));
        File file = factory.getFile(root+sourceFile);
        Partition partition = ZipFilenameEncoder.parseFilename(file.getName());

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
        return PathUtil.asDirectory(config.getRootPath()+PathUtil.stripRoot(path));
    }

}
