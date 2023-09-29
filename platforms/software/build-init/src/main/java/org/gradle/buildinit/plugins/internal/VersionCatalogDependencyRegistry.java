/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.buildinit.plugins.internal;

import com.google.common.collect.Sets;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder;
import org.gradle.util.internal.TextUtil;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks plugins, libraries and their versions used during build generation.
 */
@NonNullApi
public class VersionCatalogDependencyRegistry {
    private static final Pattern RESERVED_LIBRARY_PREFIX = Pattern.compile("^(" + String.join("|", DefaultVersionCatalogBuilder.FORBIDDEN_LIBRARY_ALIAS_PREFIX) + ")[- ]");
    private static final Pattern RESERVED_ALIAS_COMPONENT = Pattern.compile("(^|-)(" + String.join("|", Sets.union(DefaultVersionCatalogBuilder.RESERVED_ALIAS_NAMES, DefaultVersionCatalogBuilder.RESERVED_JAVA_NAMES)) + ")($|[- ])");
    private final Map<String, VersionEntry> versions = new TreeMap<>();
    private final Map<String, LibraryEntry> libraries = new TreeMap<>();
    private final Map<String, PluginEntry> plugins = new TreeMap<>();

    private final boolean fullyQualifiedAliases;

    public VersionCatalogDependencyRegistry(boolean fullyQualifiedAliases) {
        this.fullyQualifiedAliases = fullyQualifiedAliases;
    }

    public Collection<VersionEntry> getVersions() {
        return versions.values();
    }

    public Collection<LibraryEntry> getLibraries() {
        return libraries.values();
    }

    public Collection<PluginEntry> getPlugins() {
        return plugins.values();
    }

    public String registerLibrary(String module, String version) {
        String alias = fullyQualifiedAliases ? coordinatesToAlias(module) : moduleToAlias(module);
        VersionEntry versionEntry = findOrCreateVersionEntry(alias, module, version);
        LibraryEntry libraryEntry = findOrCreateLibraryEntry(alias, module, versionEntry);
        return "libs." + libraryEntry.alias.replaceAll("-", ".");
    }

    public String registerPlugin(String pluginId, String version) {
        String alias = fullyQualifiedAliases ? coordinatesToAlias(pluginId) : pluginIdToAlias(pluginId);
        PluginEntry pluginEntry = findOrCreatePluginEntry(alias, pluginId, version);
        return "libs.plugins." + pluginEntry.alias.replaceAll("-", ".");
    }

    private VersionEntry findOrCreateVersionEntry(String alias, String module, String version) {
        for (VersionEntry v : versions.values()) {
            if (v.module.equals(module) && v.version.equals(version)) {
                return v;
            }
        }
        VersionEntry v = new VersionEntry();
        v.alias = findFreeAlias(versions.keySet(), alias);
        v.module = module;
        v.version = version;
        versions.put(v.alias, v);
        return v;
    }

    private LibraryEntry findOrCreateLibraryEntry(String alias, String module, VersionEntry versionEntry) {
        for (LibraryEntry l : libraries.values()) {
            if (l.module.equals(module) && l.version.equals(versionEntry.version)) {
                return l;
            }
        }
        LibraryEntry l = new LibraryEntry();
        if (RESERVED_LIBRARY_PREFIX.matcher(alias).find()) {
            alias = "my" + TextUtil.capitalize(alias);
        }
        l.alias = findFreeAlias(libraries.keySet(), alias);
        l.module = module;
        l.version = versionEntry.version;
        l.versionRef = versionEntry.alias;
        libraries.put(l.alias, l);
        return l;
    }

    private PluginEntry findOrCreatePluginEntry(String alias, String pluginId, String version) {
        for (PluginEntry p : plugins.values()) {
            if (p.pluginId.equals(pluginId) && p.version.equals(version)) {
                return p;
            }
        }
        PluginEntry p = new PluginEntry();
        p.alias = findFreeAlias(plugins.keySet(), alias);
        p.pluginId = pluginId;
        p.version = version;
        plugins.put(p.alias, p);
        return p;
    }

    private static String pluginIdToAlias(String pluginId) {
        String[] pluginIdComponents = pluginId.split("\\.");
        return coordinatesToAlias(pluginIdComponents[pluginIdComponents.length - 1]);
    }

    private static String moduleToAlias(String module) {
        return coordinatesToAlias(module.split(":")[1]);
    }

    private static String coordinatesToAlias(String coordinates) {
        String normalizedModule = coordinates.replaceAll("[.:_]", "-").replaceAll("-(\\d)", "-v$1");
        String alias = normalizedModule.substring(0, Math.min(2, normalizedModule.length())).toLowerCase(Locale.ENGLISH) + normalizedModule.substring(Math.min(2, normalizedModule.length()));
        StringBuffer resultingAlias = new StringBuffer();
        Matcher reservedComponentsMatcher = RESERVED_ALIAS_COMPONENT.matcher(alias);
        while (reservedComponentsMatcher.find()) {
            reservedComponentsMatcher.appendReplacement(resultingAlias, "$1my" + TextUtil.capitalize(reservedComponentsMatcher.group(2)) + "$3");
        }
        reservedComponentsMatcher.appendTail(resultingAlias);
        return resultingAlias.toString();
    }

    private static String findFreeAlias(Set<String> reservedKeys, String key) {
        String nextKey = key;
        int collisionCount = 0;
        while (reservedKeys.contains(nextKey)) {
            collisionCount += 1;
            nextKey = key + "-x" + collisionCount;
        }
        return nextKey;
    }

    @NonNullApi
    public static class VersionEntry {
        String alias;
        String module;
        String version;
    }

    @NonNullApi
    public static class LibraryEntry {
        String alias;
        String module;
        String version;
        String versionRef;
    }

    @NonNullApi
    public static class PluginEntry {
        String alias;
        String pluginId;
        String version;
    }
}
