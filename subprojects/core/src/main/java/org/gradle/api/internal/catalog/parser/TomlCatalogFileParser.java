/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.catalog.parser;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Interners;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.initialization.dsl.VersionCatalogBuilder;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class TomlCatalogFileParser {
    public static final String CURRENT_VERSION = "1.0";
    private static final Splitter SPLITTER = Splitter.on(":").trimResults();
    private static final String METADATA_KEY = "metadata";
    private static final String LIBRARIES_KEY = "libraries";
    private static final String BUNDLES_KEY = "bundles";
    private static final String VERSIONS_KEY = "versions";
    private static final Set<String> TOP_LEVEL_ELEMENTS = ImmutableSet.of(
        METADATA_KEY,
        LIBRARIES_KEY,
        BUNDLES_KEY,
        VERSIONS_KEY
    );
    private static final Set<String> LIBRARY_COORDINATES = ImmutableSet.of(
        "group",
        "name",
        "version",
        "module"
    );
    private static final Set<String> VERSION_KEYS = ImmutableSet.of(
        "ref",
        "require",
        "strictly",
        "prefer",
        "reject",
        "rejectAll"
    );

    public static void parse(InputStream in, VersionCatalogBuilder builder) throws IOException {
        StrictVersionParser strictVersionParser = new StrictVersionParser(Interners.newStrongInterner());
        TomlParseResult result = Toml.parse(in);
        TomlTable metadataTable = result.getTable(METADATA_KEY);
        verifyMetadata(metadataTable);
        TomlTable librariesTable = result.getTable(LIBRARIES_KEY);
        TomlTable bundlesTable = result.getTable(BUNDLES_KEY);
        TomlTable versionsTable = result.getTable(VERSIONS_KEY);
        Sets.SetView<String> unknownTle = Sets.difference(result.keySet(), TOP_LEVEL_ELEMENTS);
        if (!unknownTle.isEmpty()) {
            throw new InvalidUserDataException("Unknown top level elements " + unknownTle);
        }
        parseLibraries(librariesTable, builder, strictVersionParser);
        parseBundles(bundlesTable, builder);
        parseVersions(versionsTable, builder, strictVersionParser);
    }

    private static void verifyMetadata(@Nullable TomlTable metadataTable) {
        if (metadataTable != null) {
            String format = metadataTable.getString("format.version");
            if (format != null && !CURRENT_VERSION.equals(format)) {
                throw new InvalidUserDataException("This catalog file format has version " + format + " which isn't supported by this Gradle version.");
            }
        }
    }

    private static void parseLibraries(@Nullable TomlTable librariesTable, VersionCatalogBuilder builder, StrictVersionParser strictVersionParser) {
        if (librariesTable == null) {
            return;
        }
        List<String> keys = librariesTable.keySet()
            .stream()
            .peek(TomlCatalogFileParser::validateAlias)
            .sorted(Comparator.comparing(String::length))
            .collect(Collectors.toList());
        for (String alias : keys) {
            parseLibrary(alias, librariesTable, builder, strictVersionParser);
        }
    }

    private static void parseVersions(@Nullable TomlTable versionsTable, VersionCatalogBuilder builder, StrictVersionParser strictVersionParser) {
        if (versionsTable == null) {
            return;
        }
        List<String> keys = versionsTable.keySet()
            .stream()
            .peek(TomlCatalogFileParser::validateAlias)
            .sorted(Comparator.comparing(String::length))
            .collect(Collectors.toList());
        for (String alias : keys) {
            parseVersion(alias, versionsTable, builder, strictVersionParser);
        }
    }

    private static void parseBundles(@Nullable TomlTable bundlesTable, VersionCatalogBuilder builder) {
        if (bundlesTable == null) {
            return;
        }
        List<String> keys = bundlesTable.keySet().stream().sorted().collect(Collectors.toList());
        for (String alias : keys) {
            List<String> bundled = expectArray("bundle", alias, bundlesTable, alias).toList().stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
            builder.bundle(alias, bundled);
        }
    }

    @Nullable
    private static String expectString(String kind, String name, TomlTable table, @Nullable String element) {
        try {
            String path = name;
            if (element != null) {
                path += "." + element;
            }
            return notEmpty(table.getString(path), element, name);
        } catch (TomlInvalidTypeException ex) {
            throw new InvalidUserDataException("On " + kind + " '" + name + "' expected a string but " + StringUtils.uncapitalize(ex.getMessage()));
        }
    }

    @Nullable
    private static TomlArray expectArray(String kind, String alias, TomlTable table, String element) {
        try {
            return table.getArray(element);
        } catch (TomlInvalidTypeException ex) {
            throw new InvalidUserDataException("On " + kind + " '" + alias + "' expected an array but " + StringUtils.uncapitalize(ex.getMessage()));
        }
    }

    @Nullable
    private static Boolean expectBoolean(String kind, String alias, TomlTable table, String element) {
        try {
            return table.getBoolean(element);
        } catch (TomlInvalidTypeException ex) {
            throw new InvalidUserDataException("On " + kind + " '" + alias + "' expected a boolean but " + StringUtils.uncapitalize(ex.getMessage()));
        }
    }

    private static void expectedKeys(TomlTable table, Set<String> allowedKeys, String context) {
        Set<String> actualKeys = table.keySet();
        if (!allowedKeys.containsAll(actualKeys)) {
            Set<String> difference = Sets.difference(actualKeys, allowedKeys);
            throw new InvalidUserDataException("On " + context + " expected to find any of " + oxfordListOf(allowedKeys, "or")
                + " but found unexpected key" + (difference.size() > 1 ? "s " : " ") + oxfordListOf(difference, "and")
                + ".");
        }
    }

    private static String oxfordListOf(Collection<String> values, String conjunction) {
        return values.stream()
            .sorted()
            .map(s -> "'" + s + "'")
            .collect(oxfordJoin(conjunction));
    }

    private static Collector<? super String, ?, String> oxfordJoin(String conjunction) {
        return Collectors.collectingAndThen(Collectors.toList(), stringList -> {
            if (stringList.isEmpty()) {
                return "";
            }
            if (stringList.size() == 1) {
                return stringList.get(0);
            }
            int bound = stringList.size() - 1;
            return String.join(", ", stringList.subList(0, bound)) + " " + conjunction + " " + stringList.get(bound);
        });
    }

    private static void parseLibrary(String alias, TomlTable librariesTable, VersionCatalogBuilder builder, StrictVersionParser strictVersionParser) {
        Object gav = librariesTable.get(alias);
        if (gav instanceof String) {
            List<String> splitted = SPLITTER.splitToList((String) gav);
            if (splitted.size() == 3) {
                String group = notEmpty(splitted.get(0), "group", alias);
                String name = notEmpty(splitted.get(1), "name", alias);
                String version = notEmpty(splitted.get(2), "version", alias);
                StrictVersionParser.RichVersion rich = strictVersionParser.parse(version);
                registerDependency(builder, alias, group, name, null, rich.require, rich.strictly, rich.prefer, null, null);
                return;
            } else {
                throw new InvalidUserDataException("Invalid GAV notation '" + gav + "' for alias '" + alias + "': it must consist of 3 parts separated by colons, eg: my.group:artifact:1.2");
            }
        }
        if (gav instanceof TomlTable) {
            expectedKeys((TomlTable) gav, LIBRARY_COORDINATES, "library declaration '" + alias + "'");
        }
        String group = expectString("alias", alias, librariesTable, "group");
        String name = expectString("alias", alias, librariesTable, "name");
        Object version = librariesTable.get(alias + ".version");
        String mi = expectString("alias", alias, librariesTable, "module");
        if (mi != null) {
            List<String> splitted = SPLITTER.splitToList(mi);
            if (splitted.size() == 2) {
                group = notEmpty(splitted.get(0), "group", alias);
                name = notEmpty(splitted.get(1), "name", alias);
            } else {
                throw new InvalidUserDataException("Invalid module notation '" + mi + "' for alias '" + alias + "': it must consist of 2 parts separated by colons, eg: my.group:artifact");
            }
        }
        String versionRef = null;
        String require = null;
        String strictly = null;
        String prefer = null;
        List<String> rejectedVersions = null;
        Boolean rejectAll = null;
        if (version instanceof String) {
            require = (String) version;
            StrictVersionParser.RichVersion richVersion = strictVersionParser.parse(require);
            require = richVersion.require;
            prefer = richVersion.prefer;
            strictly = richVersion.strictly;
        } else if (version instanceof TomlTable) {
            TomlTable versionTable = (TomlTable) version;
            expectedKeys(versionTable, VERSION_KEYS, "version declaration of alias '" + alias + "'");
            versionRef = notEmpty(versionTable.getString("ref"), "version reference", alias);
            require = notEmpty(versionTable.getString("require"), "required version", alias);
            prefer = notEmpty(versionTable.getString("prefer"), "preferred version", alias);
            strictly = notEmpty(versionTable.getString("strictly"), "strict version", alias);
            TomlArray rejectedArray = expectArray("alias", alias, versionTable, "reject");
            rejectedVersions = rejectedArray != null ? rejectedArray.toList().stream()
                .map(String::valueOf)
                .map(v -> notEmpty(v, "rejected version", alias))
                .collect(Collectors.toList()) : null;
            rejectAll = expectBoolean("alias", alias, versionTable, "rejectAll");
        } else if (version != null) {
            throw new InvalidUserDataException("On alias '" + alias + "' expected a version as a String or a table but got " + version.getClass().getSimpleName());
        }
        if (group == null) {
            throw new InvalidUserDataException("Group for alias '" + alias + "' wasn't set");
        }
        if (name == null) {
            throw new InvalidUserDataException("Name for alias '" + alias + "' wasn't set");
        }
        registerDependency(builder, alias, group, name, versionRef, require, strictly, prefer, rejectedVersions, rejectAll);
    }

    private static void parseVersion(String alias, TomlTable versionsTable, VersionCatalogBuilder builder, StrictVersionParser strictVersionParser) {
        String require = null;
        String strictly = null;
        String prefer = null;
        List<String> rejectedVersions = null;
        Boolean rejectAll = null;
        Object version = versionsTable.get(alias);
        if (version instanceof String) {
            require = notEmpty((String) version, "version", alias);
            StrictVersionParser.RichVersion richVersion = strictVersionParser.parse(require);
            require = richVersion.require;
            prefer = richVersion.prefer;
            strictly = richVersion.strictly;
        } else if (version instanceof TomlTable) {
            TomlTable versionTable = (TomlTable) version;
            require = notEmpty(versionTable.getString("require"), "required version", alias);
            prefer = notEmpty(versionTable.getString("prefer"), "preferred version", alias);
            strictly = notEmpty(versionTable.getString("strictly"), "strict version", alias);
            TomlArray rejectedArray = expectArray("alias", alias, versionTable, "reject");
            rejectedVersions = rejectedArray != null ? rejectedArray.toList().stream()
                .map(String::valueOf)
                .map(v -> notEmpty(v, "rejected version", alias))
                .collect(Collectors.toList()) : null;
            rejectAll = expectBoolean("alias", alias, versionTable, "rejectAll");
        } else if (version != null) {
            throw new InvalidUserDataException("On alias '" + alias + "' expected a version as a String or a table but got " + version.getClass().getSimpleName());
        }
        registerVersion(builder, alias, require, strictly, prefer, rejectedVersions, rejectAll);
    }

    @Nullable
    private static String notEmpty(@Nullable String string, @Nullable String member, String alias) {
        if (string == null) {
            return null;
        }
        if (string.isEmpty()) {
            throw new InvalidUserDataException((member == null ? "value" : StringUtils.capitalize(member)) + " for '" + alias + "' must not be empty");
        }
        return string;
    }

    private static void registerDependency(VersionCatalogBuilder builder,
                                           String alias,
                                           String group,
                                           String name,
                                           @Nullable String versionRef,
                                           @Nullable String require,
                                           @Nullable String strictly,
                                           @Nullable String prefer,
                                           @Nullable List<String> rejectedVersions,
                                           @Nullable Boolean rejectAll) {
        VersionCatalogBuilder.LibraryAliasBuilder aliasBuilder = builder.alias(alias).to(group, name);
        if (versionRef != null) {
            aliasBuilder.versionRef(versionRef);
            return;
        }
        aliasBuilder.version(v -> {
            if (require != null) {
                v.require(require);
            }
            if (strictly != null) {
                v.strictly(strictly);
            }
            if (prefer != null) {
                v.prefer(prefer);
            }
            if (rejectedVersions != null) {
                v.reject(rejectedVersions.toArray(new String[0]));
            }
            if (rejectAll != null && rejectAll) {
                v.rejectAll();
            }
        });
    }

    private static void registerVersion(VersionCatalogBuilder builder,
                                        String alias,
                                        @Nullable String require,
                                        @Nullable String strictly,
                                        @Nullable String prefer,
                                        @Nullable List<String> rejectedVersions,
                                        @Nullable Boolean rejectAll) {
        builder.version(alias, v -> {
            if (require != null) {
                v.require(require);
            }
            if (strictly != null) {
                v.strictly(strictly);
            }
            if (prefer != null) {
                v.prefer(prefer);
            }
            if (rejectedVersions != null) {
                v.reject(rejectedVersions.toArray(new String[0]));
            }
            if (rejectAll != null && rejectAll) {
                v.rejectAll();
            }
        });
    }

    private static void validateAlias(String alias) {
        if (!DependenciesModelHelper.ALIAS_PATTERN.matcher(alias).matches()) {
            throw new InvalidUserDataException("Invalid alias name [" + alias + "] found in TOML file: it must match the following pattern: " + DependenciesModelHelper.ALIAS_REGEX);
        }
    }

}
