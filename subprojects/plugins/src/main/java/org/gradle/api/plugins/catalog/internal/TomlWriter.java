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
import org.gradle.api.internal.std.DefaultVersionCatalog;
import org.gradle.api.internal.std.DependencyModel;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class TomlWriter {
    private final PrintWriter writer;

    TomlWriter(PrintWriter writer) {
        this.writer = writer;
    }

    public void generate(DefaultVersionCatalog model, Map<String, String> plugins) {
        writeHeader();
        writeVersions(model);
        writeLibraries(model);
        writeBundles(model);
        writePlugins(plugins);
    }

    private void writeVersions(DefaultVersionCatalog model) {
        List<String> versions = model.getVersionAliases();
        if (versions.isEmpty()) {
            return;
        }
        writeTableHeader("versions");
        for (String alias : versions) {
            writer.print(alias + " = ");
            writer.println(versionString(model.getVersion(alias).getVersion()));
        }
        writer.println();
    }

    private void writeLibraries(DefaultVersionCatalog model) {
        List<String> aliases = model.getDependencyAliases();
        if (aliases.isEmpty()) {
            return;
        }
        writeTableHeader("dependencies");
        for (String alias : aliases) {
            DependencyModel data = model.getDependencyData(alias);
            String group = data.getGroup();
            String name = data.getName();
            String versionRef = data.getVersionRef();
            ImmutableVersionConstraint version = data.getVersion();
            StringBuilder sb = new StringBuilder();
            sb.append(alias)
                .append(" = {")
                .append(keyValuePair("group", group))
                .append(", ")
                .append(keyValuePair("name", name))
                .append(", ");
            if (versionRef != null) {
                sb.append(keyValuePair("version.ref", versionRef));
            } else {
                sb.append("version = ").append(versionString(version));
            }
            sb.append(" }");
            writer.println(sb);
        }
        writer.println();
    }

    private void writeBundles(DefaultVersionCatalog model) {
        List<String> aliases = model.getBundleAliases();
        if (aliases.isEmpty()) {
            return;
        }
        writeTableHeader("bundles");
        for (String alias : aliases) {
            List<String> bundle = model.getBundle(alias).getComponents();
            writer.println(alias + " = [" + bundle.stream().map(TomlWriter::quoted).collect(Collectors.joining(", ")) + "]");
        }
        writer.println();
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
        writer.println("#");
        writer.println("# This file has been generated by Gradle and is intended to be consumed by Gradle");
        writer.println("#");
    }

    private void writePlugins(Map<String, String> plugins) {
        if (plugins.isEmpty()) {
            return;
        }
        writeTableHeader("plugins");
        plugins.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(TomlWriter::keyValuePair)
            .forEach(writer::println);
        writer.println();
    }

    private void writeTableHeader(String title) {
        writer.println("[" + title + "]");
    }

    private static String keyValuePair(Map.Entry<String, String> entry) {
        return keyValuePair(entry.getKey(), entry.getValue());
    }

    private static String keyValuePair(String key, String value) {
        return key + " = " + quoted(value);
    }

    private static String quoted(String string) {
        return "\"" + string + "\"";
    }
}
