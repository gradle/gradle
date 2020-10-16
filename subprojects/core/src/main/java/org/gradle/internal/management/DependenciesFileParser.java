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
package org.gradle.internal.management;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.initialization.dsl.DependenciesModelBuilder;
import org.gradle.plugin.use.PluginDependenciesSpec;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DependenciesFileParser {
    private static final String ALIAS_PATTERN = "^[a-z0-9-_]+$";
    private static final Pattern ALIAS_COMPILED_PATTERN = Pattern.compile(ALIAS_PATTERN);
    private static final String DEPENDENCIES_KEY = "dependencies";
    private static final String BUNDLES_KEY = "bundles";
    private static final String PLUGINS_KEY = "plugins";

    public void parse(InputStream in, DependenciesModelBuilder builder, PluginDependenciesSpec plugins) throws IOException {
        TomlParseResult result = Toml.parse(in);
        TomlTable dependenciesTable = result.getTable(DEPENDENCIES_KEY);
        TomlTable bundlesTable = result.getTable(BUNDLES_KEY);
        TomlTable pluginsTable = result.getTable(PLUGINS_KEY);
        parseDependencies(dependenciesTable, builder);
        parseBundles(bundlesTable, builder);
        parsePlugins(pluginsTable, plugins);
    }

    private void parseDependencies(@Nullable TomlTable dependenciesTable, DependenciesModelBuilder builder) {
        List<String> keys = dependenciesTable.keySet()
            .stream()
            .peek(DependenciesFileParser::validateAlias)
            .sorted(Comparator.comparing(String::length))
            .collect(Collectors.toList());
        for (String alias : keys) {
            parseDependency(alias, dependenciesTable, builder);
        }
    }

    private void parseBundles(@Nullable TomlTable bundlesTable, DependenciesModelBuilder builder) {
        List<String> keys = bundlesTable.keySet().stream().sorted().collect(Collectors.toList());
        for (String alias : keys) {
            List<String> bundled = bundlesTable.getArray(alias).toList().stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
            builder.bundle(alias, bundled);
        }
    }

    private void parsePlugins(@Nullable TomlTable pluginsTable, PluginDependenciesSpec builder) {
        List<String> keys = pluginsTable.dottedKeySet().stream().sorted().collect(Collectors.toList());
        for (String id : keys) {
            builder.id(id).version(pluginsTable.getString(id));
        }
    }

    private void parseDependency(String alias, TomlTable dependenciesTable, DependenciesModelBuilder builder) {
        String group = dependenciesTable.getString(alias + ".group");
        String name = dependenciesTable.getString(alias + ".name");
        Object version = dependenciesTable.get(alias + ".version");
        String mi = dependenciesTable.getString(alias + ".module");
        if (mi != null) {
            String[] splitted = mi.split(":");
            if (splitted.length == 2) {
                group = splitted[0];
                name = splitted[1];
            }
        }
        String require = null;
        String strictly = null;
        String prefer = null;
        List<String> rejectedVersions = null;
        Boolean rejectAll = null;
        String gav = dependenciesTable.getString(alias + ".gav");
        if (gav != null) {
            String[] splitted = gav.split(":");
            if (splitted.length == 3) {
                group = splitted[0];
                name = splitted[1];
                require = splitted[2];
            }
        }
        if (version instanceof String) {
            require = (String) version;
        } else if (version instanceof TomlTable) {
            TomlTable versionTable = (TomlTable) version;
            require = versionTable.getString("require");
            prefer = versionTable.getString("prefer");
            strictly = versionTable.getString("strictly");
            TomlArray rejectedArray = versionTable.getArray("reject");
            rejectedVersions = rejectedArray != null ? rejectedArray.toList().stream().map(String::valueOf).collect(Collectors.toList()) : null;
            rejectAll = versionTable.getBoolean("rejectAll");
        }
        registerDependency(builder, alias, group, name, require, strictly, prefer, rejectedVersions, rejectAll);
    }

    private void registerDependency(DependenciesModelBuilder builder,
                                    String alias,
                                    String group,
                                    String name,
                                    String require,
                                    String strictly,
                                    String prefer,
                                    List<String> rejectedVersions,
                                    Boolean rejectAll) {
        builder.alias(alias, group, name, v -> {
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
        if (!ALIAS_COMPILED_PATTERN.matcher(alias).matches()) {
            throw new InvalidUserDataException("Invalid alias name [" + alias + "] found in dependencies.toml: it must match the following pattern: " + ALIAS_PATTERN);
        }
    }

}
