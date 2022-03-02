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
package org.gradle.api.plugins.catalog.internal;

import com.google.common.collect.Lists;
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.catalog.DefaultVersionCatalog;
import org.gradle.api.internal.catalog.DependencyModel;
import org.gradle.api.internal.catalog.PluginModel;
import org.gradle.api.internal.catalog.parser.TomlCatalogFileParser;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class TomlWriter {
    private final static Pattern SEPARATOR = Pattern.compile("[_.-]");

    private static String normalizeForToml(String alias) {
        return SEPARATOR.matcher(alias).replaceAll("-");
    }

    private final Writer writer;

    TomlWriter(Writer writer) {
        this.writer = writer;
    }

    private TomlWriter writeLn(String line) {
        return write(line + "\n");
    }

    private TomlWriter writeLn() {
        return writeLn("");
    }

    private TomlWriter write(String text) {
        try {
            writer.write(text);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return this;
    }

    public void generate(DefaultVersionCatalog model) {
        writeHeader();
        writeMetadata();
        writeVersions(model);
        writeLibraries(model);
        writeBundles(model);
        writePlugins(model);
    }

    private void writeVersions(DefaultVersionCatalog model) {
        List<String> versions = model.getVersionAliases();
        if (versions.isEmpty()) {
            return;
        }
        writeTableHeader("versions");
        for (String alias : versions) {
            write(normalizeForToml(alias) + " = ");
            writeLn(versionString(model.getVersion(alias).getVersion()));
        }
        writeLn();
    }

    private void writeLibraries(DefaultVersionCatalog model) {
        List<String> aliases = model.getLibraryAliases();
        if (aliases.isEmpty()) {
            return;
        }
        writeTableHeader("libraries");
        for (String alias : aliases) {
            DependencyModel data = model.getDependencyData(alias);
            String group = data.getGroup();
            String name = data.getName();
            String versionRef = data.getVersionRef();
            ImmutableVersionConstraint version = data.getVersion();
            StringBuilder sb = new StringBuilder();
            sb.append(normalizeForToml(alias))
                .append(" = {")
                .append(keyValuePair("group", group))
                .append(", ")
                .append(keyValuePair("name", name))
                .append(", ");
            if (versionRef != null) {
                sb.append(keyValuePair("version.ref", normalizeForToml(versionRef)));
            } else {
                sb.append("version = ").append(versionString(version));
            }
            sb.append(" }");
            writeLn(sb.toString());
        }
        writeLn();
    }

    private void writeBundles(DefaultVersionCatalog model) {
        List<String> aliases = model.getBundleAliases();
        if (aliases.isEmpty()) {
            return;
        }
        writeTableHeader("bundles");
        for (String alias : aliases) {
            List<String> bundle = model.getBundle(alias).getComponents();
            writeLn(normalizeForToml(alias) + " = [" + bundle.stream()
                .map(TomlWriter::normalizeForToml)
                .map(TomlWriter::quoted)
                .collect(Collectors.joining(", ")) + "]");
        }
        writeLn();
    }

    private void writePlugins(DefaultVersionCatalog model) {
        List<String> aliases = model.getPluginAliases();
        if (aliases.isEmpty()) {
            return;
        }
        writeTableHeader("plugins");
        for (String alias : aliases) {
            PluginModel data = model.getPlugin(alias);
            String id = data.getId();
            String versionRef = data.getVersionRef();
            ImmutableVersionConstraint version = data.getVersion();
            StringBuilder sb = new StringBuilder();
            sb.append(normalizeForToml(alias))
                .append(" = {")
                .append(keyValuePair("id", id))
                .append(", ");
            if (versionRef != null) {
                sb.append(keyValuePair("version.ref", normalizeForToml(versionRef)));
            } else {
                sb.append("version = ").append(versionString(version));
            }
            sb.append(" }");
            writeLn(sb.toString());
        }
        writeLn();
    }

    private static String versionString(ImmutableVersionConstraint version) {
        String requiredVersion = version.getRequiredVersion();
        String strictVersion = version.getStrictVersion();
        String preferredVersion = version.getPreferredVersion();
        List<String> rejectedVersions = version.getRejectedVersions();
        StringBuilder sb = new StringBuilder();
        if (rejectedVersions.isEmpty() && strictVersion.isEmpty() && preferredVersion.isEmpty()) {
            // typical shortcut case, "foo='1.2'"
            sb.append(quoted(requiredVersion));
            return sb.toString();
        }
        sb.append("{ ");
        List<String> parts = Lists.newArrayList();
        if (!strictVersion.isEmpty()) {
            parts.add(keyValuePair("strictly", strictVersion));
        }
        if (!preferredVersion.isEmpty()) {
            parts.add(keyValuePair("prefer", preferredVersion));
        }
        if (!requiredVersion.isEmpty() && strictVersion.isEmpty()) {
            parts.add(keyValuePair("require", requiredVersion));
        }
        if (!rejectedVersions.isEmpty()) {
            if (rejectedVersions.contains("+")) {
                parts.add("rejectAll = true");
            } else {
                parts.add("reject = [" + rejectedVersions.stream().map(TomlWriter::quoted).collect(Collectors.joining(", ")) + "]");
            }
        }
        sb.append(String.join(", ", parts)).append(" }");
        return sb.toString();
    }

    private void writeHeader() {
        writeLn("#");
        writeLn("# This file has been generated by Gradle and is intended to be consumed by Gradle");
        writeLn("#");
    }

    private void writeMetadata() {
        writeLn("[metadata]");
        writeLn("format.version = \"" + TomlCatalogFileParser.CURRENT_VERSION + "\"");
        writeLn();
    }

    private void writeTableHeader(String title) {
        writeLn("[" + title + "]");
    }

    private static String keyValuePair(String key, String value) {
        return key + " = " + quoted(value);
    }

    private static String quoted(String string) {
        return "\"" + string + "\"";
    }
}
