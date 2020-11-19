/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.std;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Interners;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.initialization.dsl.VersionCatalogBuilder;
import org.gradle.plugin.use.PluginDependenciesSpec;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TomlDependenciesFileParser {
    private static final Splitter SPLITTER = Splitter.on(":").trimResults();
    private static final String DEPENDENCIES_KEY = "dependencies";
    private static final String BUNDLES_KEY = "bundles";
    private static final String PLUGINS_KEY = "plugins";
    private static final String VERSIONS_KEY = "versions";
    private static final Set<String> TOP_LEVEL_ELEMENTS = ImmutableSet.of(
        DEPENDENCIES_KEY,
        BUNDLES_KEY,
        PLUGINS_KEY,
        VERSIONS_KEY
    );

    public static void parse(InputStream in, VersionCatalogBuilder builder, PluginDependenciesSpec plugins, ImportConfiguration importConfig) throws IOException {
        StrictVersionParser strictVersionParser = new StrictVersionParser(Interners.newStrongInterner());
        TomlParseResult result = Toml.parse(in);
        TomlTable dependenciesTable = result.getTable(DEPENDENCIES_KEY);
        TomlTable bundlesTable = result.getTable(BUNDLES_KEY);
        TomlTable pluginsTable = result.getTable(PLUGINS_KEY);
        TomlTable versionsTable = result.getTable(VERSIONS_KEY);
        Sets.SetView<String> unknownTle = Sets.difference(result.keySet(), TOP_LEVEL_ELEMENTS);
        if (!unknownTle.isEmpty()) {
            throw new InvalidUserDataException("Unknown top level elements " + unknownTle);
        }
        parseDependencies(dependenciesTable, builder, strictVersionParser, importConfig);
        parseBundles(bundlesTable, builder, importConfig);
        parsePlugins(pluginsTable, plugins, importConfig);
        parseVersions(versionsTable, builder, strictVersionParser, importConfig);
    }

    private static void parseDependencies(@Nullable TomlTable dependenciesTable, VersionCatalogBuilder builder, StrictVersionParser strictVersionParser, ImportConfiguration importConfig) {
        if (dependenciesTable == null) {
            return;
        }
        List<String> keys = dependenciesTable.keySet()
            .stream()
            .peek(TomlDependenciesFileParser::validateAlias)
            .sorted(Comparator.comparing(String::length))
            .collect(Collectors.toList());
        for (String alias : keys) {
            if (importConfig.includeDependency(alias)) {
                parseDependency(alias, dependenciesTable, builder, strictVersionParser);
            }
        }
    }

    private static void parseVersions(@Nullable TomlTable versionsTable, VersionCatalogBuilder builder, StrictVersionParser strictVersionParser, ImportConfiguration importConfig) {
        if (versionsTable == null) {
            return;
        }
        List<String> keys = versionsTable.keySet()
            .stream()
            .peek(TomlDependenciesFileParser::validateAlias)
            .sorted(Comparator.comparing(String::length))
            .collect(Collectors.toList());
        for (String alias : keys) {
            if (importConfig.includeVersion(alias)) {
                parseVersion(alias, versionsTable, builder, strictVersionParser);
            }
        }
    }

    private static void parseBundles(@Nullable TomlTable bundlesTable, VersionCatalogBuilder builder, ImportConfiguration importConfig) {
        if (bundlesTable == null) {
            return;
        }
        List<String> keys = bundlesTable.keySet().stream().sorted().collect(Collectors.toList());
        for (String alias : keys) {
            if (importConfig.includeBundle(alias)) {
                List<String> bundled = expectArray("bundle", alias, bundlesTable, alias).toList().stream()
                    .map(String::valueOf)
                    .collect(Collectors.toList());
                builder.bundle(alias, bundled);
            }
        }
    }

    private static void parsePlugins(@Nullable TomlTable pluginsTable, PluginDependenciesSpec builder, ImportConfiguration importConfig) {
        if (pluginsTable == null) {
            return;
        }
        List<String> keys = pluginsTable.dottedKeySet().stream().sorted().collect(Collectors.toList());
        for (String id : keys) {
            if (importConfig.includePlugin(id)) {
                builder.id(id).version(expectString("plugin", id, pluginsTable, null));
            }
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

    private static void parseDependency(String alias, TomlTable dependenciesTable, VersionCatalogBuilder builder, StrictVersionParser strictVersionParser) {
        Object gav = dependenciesTable.get(alias);
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
        String group = expectString("alias", alias, dependenciesTable, "group");
        String name = expectString("alias", alias, dependenciesTable, "name");
        Object version = dependenciesTable.get(alias + ".version");
        String mi = expectString("alias", alias, dependenciesTable, "module");
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
            throw new InvalidUserDataException("Invalid alias name [" + alias + "] found in dependencies.toml: it must match the following pattern: " + DependenciesModelHelper.ALIAS_REGEX);
        }
    }

}
