package com.cleo.labs.connector.zip;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.cleo.connector.api.ConnectorConfig;
import com.cleo.connector.api.annotations.Client;
import com.cleo.connector.api.annotations.Connector;
import com.cleo.connector.api.annotations.ExcludeType;
import com.cleo.connector.api.annotations.Info;
import com.cleo.connector.api.annotations.Property;
import com.cleo.connector.api.interfaces.IConnectorProperty;
import com.cleo.connector.api.property.CommonProperties;
import com.cleo.connector.api.property.CommonProperty;
import com.cleo.connector.api.property.CommonPropertyGroups;
import com.cleo.connector.api.property.PropertyBuilder;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;

@Connector(scheme = "Zip", description = "Convert a directory into a Zipped stream.",
           excludeType = { @ExcludeType(type = ExcludeType.SentReceivedBoxes),
                           @ExcludeType(type = ExcludeType.Exchange) })
@Client(ZipConnectorClient.class)
public class ZipConnectorSchema extends ConnectorConfig {
    @Property
    final IConnectorProperty<String> rootPath = new PropertyBuilder<>("RootPath", "")
            .setRequired(true)
            .setAllowedInSetCommand(true)
            .setDescription("The directory to zip to or from.")
            .setType(IConnectorProperty.Type.PathType)
            .build();

    public static final String DEFAULT = "default";
    public static final String NONE = "none";

    @Property
    final public IConnectorProperty<String> compressionLevel = new PropertyBuilder<>("CompressionLevel", DEFAULT)
            .setAllowedInSetCommand(false)
            .setDescription("Compression level none (0), 1-9, or default compression.")
            .setPossibleValues(DEFAULT,NONE,"1","2","3","4","5","6","7","8","9")
            .build();

    @Property
    final public IConnectorProperty<String> exclusions = new PropertyBuilder<>("Exclusions", "")
            .setAllowedInSetCommand(false)
            .setDescription("A list of file/path patterns to exclude from zipping and unzipping.")
            .setExtendedClass(ExclusionTableProperty.class)
            .setGroup(CommonPropertyGroups.ConnectAdvanced)
            .build();

    @Property
    final IConnectorProperty<String> select = new PropertyBuilder<>("Select", "")
            .setAllowedInSetCommand(true)
            .setDescription("A file/path name or pattern to select for zipping.")
            .setGroup(CommonPropertyGroups.ConnectAdvanced)
            .build();

    @Property
    final IConnectorProperty<String> remoteDirectoryListing = new PropertyBuilder<>("RemoteDirectoryListing", "")
            .setAllowedInSetCommand(true)
            .setDescription("A file or URI for retrieving a remote directory listing,"+
                            " and only changes found locally will be zipped.")
            .setGroup(CommonPropertyGroups.ConnectAdvanced)
            .build();

    @Property
    final IConnectorProperty<Long> remoteDirectoryListingTimeout = new PropertyBuilder<>("RemoteDirectoryListingTimeout", 10_000L)
            .setDescription("Timeout in milliseconds for reading the Remote Directory Listing."+
                            " This also affects how long it may take for a GET action to respond to"+
                            " a request to stop.")
            .setGroup(CommonPropertyGroups.ConnectAdvanced)
            .build();

    @Property
    final IConnectorProperty<Boolean> dontZipEmptyDirectories = new PropertyBuilder<>("DontZipEmptyDirectories", false)
            .setAllowedInSetCommand(false)
            .setDescription("Don't include empty directories when zipping.")
            .setGroup(CommonPropertyGroups.ConnectAdvanced)
            .build();

    @Property
    final IConnectorProperty<String> zipSizeThreshold = new PropertyBuilder<>("ZipSizeThreshold", "")
            .setAllowedInSetCommand(true)
            .setDescription("Set to split large zip files into parts when the size threshold is crossed.")
            .addPossibleRegexes("\\d+(?i:[kmgt]b?)?")
            .setGroup(CommonPropertyGroups.ConnectAdvanced)
            .build();

    public enum UnzipMode {
        unzip("unzip"), unzipAndLog("unzip and log"), log("log"), preflight("preflight");
        private String legend;
        UnzipMode(String legend) {
            this.legend = legend;
        }
        public String legend() {
            return legend;
        }
        private static Map<String,UnzipMode> lookup = new HashMap<>();
        static {
            for (UnzipMode mode : UnzipMode.values()) {
                lookup.put(mode.legend(), mode);
            }
        }
        public static UnzipMode lookup(String legend) {
            if (!lookup.containsKey(legend)) {
                throw new IllegalArgumentException();
            }
            return lookup.get(legend);
        }
    };

    @Property
    final IConnectorProperty<String> unzipMode = new PropertyBuilder<>("UnzipMode", UnzipMode.unzip.name())
            .setAllowedInSetCommand(true)
            .setDescription("Select \"log\" to log directories and files that would be created without "+
                "actually creating them. Select \"preflight\" to check for possible collisions that would "+
                "occur when unzipping, failing the transfer if any are detected. Select \"unzip\" (the default) "+
                "to actually unzip, overwriting any existing conflicting files.")
            .setGroup(CommonPropertyGroups.ConnectAdvanced)
            .setPossibleValues("", UnzipMode.unzip.legend(),
                    UnzipMode.unzipAndLog.legend(),
                    UnzipMode.log.legend(),
                    UnzipMode.preflight.legend())
            .build();

    @Property
    final IConnectorProperty<Boolean> suppressDirectoryCreation = new PropertyBuilder<>("SuppressDirectoryCreation", false)
            .setAllowedInSetCommand(true)
            .setDescription("Suppress the creation of directories. "+
                 "Use with cloud storage infrastructures that don't require directories.")
            .setGroup(CommonPropertyGroups.ConnectAdvanced)
            .build();

    @Property
    final IConnectorProperty<Boolean> unzipRootFilesLast = new PropertyBuilder<>("UnzipRootFilesLast", false)
            .setAllowedInSetCommand(true)
            .setDescription("While unzipping, save top-level files (those not in a subdirectory) in "+
                 "a temporary folder until the last file is unzipped.")
            .setGroup(CommonPropertyGroups.ConnectAdvanced)
            .build();

    @Property
    final IConnectorProperty<String> retrieveDirectorySort = CommonProperties.of(CommonProperty.RetrieveDirectorySort);

    @Property
    final IConnectorProperty<Boolean> enableDebug = CommonProperties.of(CommonProperty.EnableDebug);

    @Info
    protected static String info() throws IOException {
        return Resources.toString(ZipConnectorSchema.class.getResource("info.txt"), Charsets.UTF_8);
    }
}
