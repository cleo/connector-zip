package com.cleo.labs.connector.zip;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.Deflater;

import com.cleo.connector.api.property.ConnectorPropertyException;
import com.cleo.labs.connector.zip.ExclusionTableProperty.Exclusion;
import com.cleo.labs.connector.zip.ZipConnectorSchema.UnzipMode;
import com.cleo.labs.util.zip.Finder.DirectoryMode;
import com.cleo.labs.util.zip.ZapFoundOutputStream;
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
        } else if (value.equalsIgnoreCase(ZipConnectorSchema.ZAP)) {
            return ZapFoundOutputStream.ZAP_LEVEL;
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

    /* Parses an optionally suffixed length:
     * <ul>
     * <li><b>nnnK</b> nnn KB (technically "kibibytes", * 1024)</li>
     * <li><b>nnnM</b> nnn MB ("mebibytes", * 1024^2)</li>
     * <li><b>nnnG</b> nnn GB ("gibibytes", * 1024^3)</li>
     * <li><b>nnnT</b> nnn TB ("tebibytes", * 1024^4)</li>
     * </ul>
     * Note that suffixes may be upper or lower case.  A trailing "b"
     * (e.g. kb, mb, ...) is tolerated but not required.
     * @param length the string to parse
     * @return the parsed long
     * @throws {@link NumberFormatException}
     * @see {@link Long#parseLong(String)}
     */
    public static long parseLength(String length) {
        if (!Strings.isNullOrEmpty(length)) {
            long multiplier = 1L;
            int  check = length.length()-1;
            if (check>=0) {
                char suffix = length.charAt(check);
                if ((suffix=='b' || suffix=='B') && check>0) {
                    check--;
                    suffix = length.charAt(check);
                }
                switch (suffix) {
                case 'k': case 'K': multiplier =                   1024L; break;
                case 'm': case 'M': multiplier =             1024L*1024L; break;
                case 'g': case 'G': multiplier =       1024L*1024L*1024L; break;
                case 't': case 'T': multiplier = 1024L*1024L*1024L*1024L; break;
                default:
                }
                if (multiplier != 1) {
                    length = length.substring(0, check);
                }
            }
            return Long.parseLong(length)*multiplier;
        }
        return 0L;
    }

    private static String globByDefault(String pattern) {
        if (pattern==null || pattern.isEmpty()) {
            return null;
        } else if (!pattern.startsWith("regex:") && !pattern.startsWith("glob:")) {
            pattern = "glob:"+pattern;
        }
        return pattern;
    }

    public String[] getExclusions() {
        try {
            String value = schema.exclusions.getValue(client);
            Exclusion[] exclusions = ExclusionTableProperty.toExclusions(value);
            String[] patterns = Stream.of(exclusions)
                    .filter(Exclusion::isEnabled)
                    .map(Exclusion::getExclusionPattern)
                    .map(ZipConnectorConfig::globByDefault)
                    .toArray(String[]::new);
            return patterns;
        } catch (ConnectorPropertyException e) {
            return new String[0];
        }
    }

    public String getSelect() {
        try {
            return globByDefault(schema.select.getValue(client));
        } catch (ConnectorPropertyException e) {
            return null;
        }
    }

    public String getRemoteDirectoryListing() {
        try {
            return schema.remoteDirectoryListing.getValue(client);
        } catch (ConnectorPropertyException e) {
            return null;
        }
    }

    public long getRemoteDirectoryListingTimeout() {
        try {
            return schema.remoteDirectoryListingTimeout.getValue(client);
        } catch (ConnectorPropertyException e) {
            return 10L;
        }
    }

    public boolean getReplicateDeletes() {
        try {
            return schema.replicateDeletes.getValue(client);
        } catch (ConnectorPropertyException e) {
            return false;
        }
    }

    public TimeUnit getRemoteDirectoryListingTimeoutUnit() {
        return TimeUnit.SECONDS;
    }

    public DirectoryMode getDirectoryMode() {
        try {
            // when select is provided use excludeEmpty always
            String select = schema.select.getValue(client);
            if (select!=null && !select.isEmpty()) {
                return DirectoryMode.excludeEmpty;
            }
            // otherwise obey checkbox
            boolean value = schema.dontZipEmptyDirectories.getValue(client);
            return value ? DirectoryMode.excludeEmpty : DirectoryMode.include;
        } catch (ConnectorPropertyException e) {
            return DirectoryMode.include;
        }
    }

    public UnzipMode getUnzipMode() {
        try {
            String value = schema.unzipMode.getValue(client);
            return UnzipMode.lookup(value);
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