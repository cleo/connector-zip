package com.cleo.labs.connector.zip;

import java.io.IOException;

import com.cleo.connector.api.ConnectorConfig;
import com.cleo.connector.api.annotations.Client;
import com.cleo.connector.api.annotations.Connector;
import com.cleo.connector.api.annotations.ExcludeType;
import com.cleo.connector.api.annotations.Info;
import com.cleo.connector.api.annotations.Property;
import com.cleo.connector.api.interfaces.IConnectorProperty;
import com.cleo.connector.api.property.CommonProperties;
import com.cleo.connector.api.property.CommonProperty;
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
    final public IConnectorProperty<String> compressionLevel = new PropertyBuilder<>("CompressionLevel",DEFAULT)
            .setAllowedInSetCommand(false)
            .setDescription("Compression level none (0), 1-9, or default compression.")
            .setPossibleValues(DEFAULT,NONE,"1","2","3","4","5","6","7","8","9")
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
