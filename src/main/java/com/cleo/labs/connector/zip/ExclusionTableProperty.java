package com.cleo.labs.connector.zip;

import static com.cleo.connector.array.ArrayFeature.OrderingColumns;

import com.cleo.connector.api.annotations.Array;
import com.cleo.connector.api.annotations.Display;
import com.cleo.connector.api.annotations.Property;
import com.cleo.connector.api.interfaces.IConnectorProperty;
import com.cleo.connector.api.property.PropertyBuilder;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

/**
 * Profile table extended property - @Array of subproperties (identified by @Property)
 */
@Array(features = { OrderingColumns })
public class ExclusionTableProperty {

    private static final Gson GSON = new Gson();

    /**
     * Display value for the Profile Table property
     * @param value the Routing Table property value (a JSON array)
     * @return "n Records" (or "1 Record")
     */
    @Display
    public String display(String value) {
        int size = toExclusions(value).length;
        return String.format("%d Exclusion%s", size, size==1?"":"s");
    }

    @Property
    final IConnectorProperty<Boolean> enabled = new PropertyBuilder<>("Enabled", true)
        .setRequired(true)
        .build();

    @Property
    final IConnectorProperty<String> exclusionPattern = new PropertyBuilder<>("ExclusionPattern", "")
        .setRequired(true)
        .setDescription("File/path pattern to exclude from zipping or unzipping, "+
            "either glob:glob-pattern or regex:regex-pattern "+
            "(see https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystem.html#getPathMatcher-java.lang.String-)")
        .build();

    /**
     * Java Bean representation of a table row.
     */
    public static class Exclusion {
        private boolean enabled;
        @SerializedName("exclusionpattern")
        private String exclusionPattern;

        public Exclusion() {
            this.enabled = false;
            this.exclusionPattern = null;
        }
        public boolean isEnabled() {
            return enabled;
        }
        public Exclusion setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        public String getExclusionPattern() {
            return exclusionPattern;
        }
        public Exclusion setExclusionPattern(String exclusionPattern) {
            this.exclusionPattern = exclusionPattern;
            return this;
        }
    }

    /**
     * Deserialize the JSON array into a Java {@code Exclusion[]}.
     * @param value the JSON array (may be {@code null})
     * @return a {@code Exclusion[]}, may be {@code Exclusion[0]}, but never {@code null}
     */
    public static Exclusion[] toExclusions(String value) {
        return Strings.isNullOrEmpty(value) ? new Exclusion[0] : GSON.fromJson(value, Exclusion[].class);
    }
}