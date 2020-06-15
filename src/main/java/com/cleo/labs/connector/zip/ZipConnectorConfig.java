package com.cleo.labs.connector.zip;

import java.util.zip.Deflater;

import com.cleo.connector.api.property.ConnectorPropertyException;
import com.google.common.base.Strings;

public class ZipConnectorConfig {
    private ZipConnectorClient client;
    private ZipConnectorSchema schema;

    public ZipConnectorConfig(ZipConnectorClient client, ZipConnectorSchema schema) {
        this.client = client;
        this.schema = schema;
    }

    public String getRootPath() throws ConnectorPropertyException {
        return schema.rootPath.getValue(client);
    }

    public int getCompressionLevel() throws ConnectorPropertyException {
        String value = schema.compressionLevel.getValue(client);
        if (Strings.isNullOrEmpty(value) || value.equalsIgnoreCase(ZipConnectorSchema.DEFAULT)) {
            return Deflater.DEFAULT_COMPRESSION;
        } else if (value.equalsIgnoreCase(ZipConnectorSchema.NONE)) {
            return Deflater.NO_COMPRESSION;
        } else {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new ConnectorPropertyException(e);
            }
        }
    }

    public boolean getSimulateUnzip() {
        try {
            return schema.simulateUnzip.getValue(client);
        } catch (ConnectorPropertyException e) {
            return false;
        }
    }
}