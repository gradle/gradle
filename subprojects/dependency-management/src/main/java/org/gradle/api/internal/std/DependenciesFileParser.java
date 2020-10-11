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
    private static final String ALIAS_PATTERN = "^[a-z0-9-_]+$";
    private static final Pattern ALIAS_COMPILED_PATTERN = Pattern.compile(ALIAS_PATTERN);
    private static final String DEPENDENCIES_KEY = "dependencies";
    private static final String BUNDLES_KEY = "bundles";

    private final Interner<String> strings = Interners.newStrongInterner();
    private final Interner<DependencyVersionModel> versions = Interners.newStrongInterner();

    public AllDependenciesModel parse(InputStream in) throws IOException {
        TomlParseResult result = Toml.parse(in);
        TomlTable dependenciesTable = result.getTable(DEPENDENCIES_KEY);
        TomlTable bundlesTable = result.getTable(BUNDLES_KEY);
        Map<String, DependencyModel> dependencies = ImmutableMap.copyOf(parseDependencies(dependenciesTable));
        Map<String, List<String>> bundles = ImmutableMap.copyOf(parseBundles(bundlesTable, dependencies));
        return new AllDependenciesModel(dependencies, bundles);
    }

    private Map<String, DependencyModel> parseDependencies(@Nullable TomlTable dependenciesTable) {
        if (dependenciesTable == null) {
            return Collections.emptyMap();
        }
        List<String> keys = dependenciesTable.keySet()
            .stream()
            .peek(DependenciesFileParser::validateAlias)
            .sorted(Comparator.comparing(String::length))
            .collect(Collectors.toList());
        Map<String, DependencyModel> dependencies = Maps.newLinkedHashMap();
        for (String alias : keys) {
            DependencyModel data = parseDependency(alias, dependenciesTable, dependencies);
            dependencies.put(alias, data);
        }
        return dependencies;
    }

    private Map<String, List<String>> parseBundles(@Nullable TomlTable bundlesTable, Map<String, DependencyModel> dependencies) {
        if (bundlesTable == null) {
            return Collections.emptyMap();
        }
        List<String> keys = bundlesTable.keySet().stream().sorted().collect(Collectors.toList());
        Map<String, List<String>> bundles = Maps.newLinkedHashMap();
        for (String alias : keys) {
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

    private DependencyModel parseDependency(String alias, TomlTable dependenciesTable, Map<String, DependencyModel> dependencies) {
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
        DependencyVersionModel versionModel = null;
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
            rejectedVersions = rejectedArray != null ? rejectedArray.toList().stream().map(String::valueOf).map(this::intern).collect(Collectors.toList()) : null;
            rejectAll = versionTable.getBoolean("rejectAll");
        }
        if (require != null || prefer != null || strictly != null || rejectedVersions != null || rejectAll != null) {
            versionModel = versions.intern(new DependencyVersionModel(intern(prefer), intern(require), intern(strictly), rejectedVersions, rejectAll));
        }
        // We have implicit inheritance from parents so for all "null" values we're going to look into the parent
        int idx;
        String cur = alias;
        while ((idx = cur.lastIndexOf("_")) > 0) {
            cur = cur.substring(0, idx);
            DependencyModel parent = dependencies.get(cur);
            if (parent != null) {
                if (group == null) {
                    group = parent.getGroup();
                }
                if (name == null) {
                    name = parent.getName();
                }
                if (versionModel == null) {
                    versionModel = parent.getVersion();
                }
                break;
            }
        }

        return new DependencyModel(
            intern(group),
            intern(name),
            versionModel
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
        FileInputStream in = new FileInputStream("/home/cchampeau/DEV/PROJECTS/repros/mylib/gradle/dependencies.toml");
        AllDependenciesModel parse = dependenciesFileParser.parse(in);
        System.out.println("parse = " + parse);
    }
}
