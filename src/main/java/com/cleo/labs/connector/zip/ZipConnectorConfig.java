package com.cleo.labs.connector.zip;

import java.util.stream.Stream;
import java.util.zip.Deflater;

import com.cleo.connector.api.property.ConnectorPropertyException;
import com.cleo.labs.connector.zip.ExclusionTableProperty.Exclusion;
import com.cleo.labs.connector.zip.ZipConnectorSchema.UnzipMode;
import com.cleo.labs.util.zip.Finder.DirectoryMode;
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

    public boolean getDontZipEmptyDirectories() {
        try {
            return schema.dontZipEmptyDirectories.getValue(client);
        } catch (ConnectorPropertyException e) {
            return false;
        }
    }

    public String[] getExclusions() {
        try {
            String value = schema.exclusions.getValue(client);
            Exclusion[] exclusions = ExclusionTableProperty.toExclusions(value);
            String[] patterns = Stream.of(exclusions)
                    .filter(Exclusion::isEnabled)
                    .map(Exclusion::getExclusionPattern)
                    .toArray(String[]::new);
            return patterns;
        } catch (ConnectorPropertyException e) {
            return new String[0];
        }
    }

    public DirectoryMode getDirectoryMode() {
        try {
            boolean value = schema.dontZipEmptyDirectories.getValue(client);
            return value ? DirectoryMode.excludeEmpty : DirectoryMode.include;
        } catch (ConnectorPropertyException e) {
            return DirectoryMode.include;
        }
    }

    public UnzipMode getUnzipMode() {
        try {
            String value = schema.unzipMode.getValue(client);
            return UnzipMode.valueOf(value);
        } catch (ConnectorPropertyException | IllegalArgumentException | NullPointerException e) {
            // if value is null or empty we'll wind up here
            return UnzipMode.unzip;
        }
    }

    public boolean getSuppressDirectoryCreation() {
        try {
            return schema.suppressDirectoryCreation.getValue(client);
        } catch (ConnectorPropertyException e) {
            return false;
        }
    }

    public boolean unzipRootFilesLast() {
        try {
            return schema.unzipRootFilesLast.getValue(client);
        } catch (ConnectorPropertyException e) {
            return false;
        }
    }
}