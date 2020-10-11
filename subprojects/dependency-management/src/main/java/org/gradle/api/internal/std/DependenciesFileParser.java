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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.InvalidUserDataException;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import javax.annotation.Nullable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DependenciesFileParser {
    private static final String ALIAS_PATTERN = "^[a-z0-9.-]+$";
    private static final Pattern ALIAS_COMPILED_PATTERN = Pattern.compile(ALIAS_PATTERN);
    private static final String DEPENDENCIES_KEY = "dependencies";
    private static final String BUNDLES_KEY = "bundles";

    private final Interner<String> strings = Interners.newStrongInterner();

    public DependenciesConfig parse(InputStream in) throws IOException {
        TomlParseResult result = Toml.parse(in);
        TomlTable dependenciesTable = result.getTable(DEPENDENCIES_KEY);
        TomlTable bundlesTable = result.getTable(BUNDLES_KEY);
        Map<String, DependencyData> dependencies = ImmutableMap.copyOf(parseDependencies(dependenciesTable));
        Map<String, List<String>> bundles = ImmutableMap.copyOf(parseBundles(bundlesTable, dependencies));
        return new DependenciesConfig(dependencies, bundles);
    }

    private Map<String, DependencyData> parseDependencies(@Nullable TomlTable dependenciesTable) {
        if (dependenciesTable == null) {
            return Collections.emptyMap();
        }
        List<String> keys = Sets.difference(dependenciesTable.dottedKeySet(true), dependenciesTable.dottedKeySet(false))
            .stream()
            .peek(DependenciesFileParser::validateAlias)
            .sorted(Comparator.comparing(String::length))
            .collect(Collectors.toList());
        Map<String, DependencyData> dependencies = Maps.newLinkedHashMap();
        for (String alias : keys) {
            DependencyData data = parseDependency(alias, dependenciesTable, dependencies);
            dependencies.put(alias, data);
        }
        return dependencies;
    }

    private Map<String, List<String>> parseBundles(@Nullable TomlTable bundlesTable, Map<String, DependencyData> dependencies) {
        if (bundlesTable == null) {
            return Collections.emptyMap();
        }
        List<String> keys = bundlesTable.keySet().stream().sorted().collect(Collectors.toList());
        Map<String, List<String>> bundles = Maps.newLinkedHashMap();
        for (String alias : keys) {
            if (dependencies.containsKey(alias)) {
                throw new InvalidUserDataException("A bundle with name '" + alias + "' is declared but a dependency with the same name already exists");
            }
            List<String> bundled = bundlesTable.getArray(alias).toList().stream()
                .map(String::valueOf)
                .peek(d -> {
                    if (!dependencies.containsKey(d)) {
                        throw new InvalidUserDataException("A bundle with name '" + alias + "' declares a dependency on '" + d + "' which is not declared in the same file");
                    }
                })
                .collect(Collectors.toList());
            bundles.put(alias, bundled);
        }
        return bundles;
    }

    private DependencyData parseDependency(String alias, TomlTable dependenciesTable, Map<String, DependencyData> dependencies) {
        String group = dependenciesTable.getString(alias + ".group");
        String name = dependenciesTable.getString(alias + ".name");
        String version = dependenciesTable.getString(alias + ".version");
        String require = dependenciesTable.getString(alias + ".require");
        if (require == null) {
            // if the user sets 'version', we assume it's a 'require'
            require = version;
        }
        String prefer = dependenciesTable.getString(alias + ".prefer");
        String strictly = dependenciesTable.getString(alias + ".strictly");
        String gav = dependenciesTable.getString(alias + ".gav");
        if (gav != null) {
            String[] splitted = gav.split(":");
            if (splitted.length == 3) {
                group = splitted[0];
                name = splitted[1];
                require = splitted[2];
            }
        }
        String mi = dependenciesTable.getString(alias + ".module");
        if (mi != null) {
            String[] splitted = mi.split(":");
            if (splitted.length == 2) {
                group = splitted[0];
                name = splitted[1];
            }
        }
        TomlArray rejectedArray = dependenciesTable.getArray(alias + ".reject");
        List<String> rejectedVersions = rejectedArray != null ? rejectedArray.toList().stream().map(String::valueOf).map(this::intern).collect(Collectors.toList()) : null;
        Boolean rejectAll = dependenciesTable.getBoolean(alias + ".rejectAll");

        // We have implicit inheritance from parents so for all "null" values we're going to look into the parent
        int idx;
        String cur = alias;
        while ((idx = cur.lastIndexOf(".")) > 0) {
            cur = cur.substring(0, idx);
            DependencyData parent = dependencies.get(cur);
            if (parent != null) {
                if (group == null) {
                    group = parent.getGroup();
                }
                if (name == null) {
                    name = parent.getName();
                }
                if (prefer == null) {
                    prefer = parent.getPreferredVersion();
                }
                if (require == null) {
                    require = parent.getRequiredVersion();
                }
                if (strictly == null) {
                    strictly = parent.getStrictlyVersion();
                }
                if (rejectedVersions == null) {
                    rejectedVersions = parent.getRejectedVersions();
                }
                if (rejectAll != null) {
                    rejectAll = parent.getRejectAll();
                }
                break;
            }
        }

        return new DependencyData(
            intern(group),
            intern(name),
            intern(prefer),
            intern(require),
            intern(strictly),
            rejectedVersions,
            rejectAll
        );
    }

    private static void validateAlias(String alias) {
        if (!ALIAS_COMPILED_PATTERN.matcher(alias).matches()) {
            throw new InvalidUserDataException("Invalid alias name [" + alias + "] found in dependencies.toml: it must match the following pattern: " + ALIAS_PATTERN);
        }
    }

    @Nullable
    private String intern(@Nullable String string) {
        if (string == null) {
            return null;
        }
        return strings.intern(string);
    }


    public static void main(String[] args) throws IOException {
        DependenciesFileParser dependenciesFileParser = new DependenciesFileParser();
        FileInputStream in = new FileInputStream("/home/cchampeau/DEV/PROJECTS/repros/mylib/dependencies.toml");
        DependenciesConfig parse = dependenciesFileParser.parse(in);
        System.out.println("parse = " + parse);
    }
}
