/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api.tasks.diagnostics;

import com.google.gson.stream.JsonWriter;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.internal.ConfigurationFinder;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.serialization.Cached;
import org.gradle.util.GradleVersion;
import org.gradle.work.DisableCachingByDefault;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Generates a protobom SBOM for the resolved dependency graph of a configuration.
 *
 * @since 9.10.0
 */
@SuppressWarnings("this-escape")
@DisableCachingByDefault(because = "Not worth caching")
@Incubating
@NullMarked
public abstract class SbomReportTask extends DefaultTask {

    private final Cached<Graph> graph = Cached.of(this::collectGraph);

    /**
     * The configurations to include in the SBOM. Defaults to {@code runtimeClasspath}.
     *
     * @since 9.10.0
     */
    @Input
    public abstract ListProperty<String> getConfigurations();

    /**
     * The file the SBOM is written to. Defaults to {@code build/reports/sbom/sbom.json}.
     *
     * @since 9.10.0
     */
    @OutputFile
    public abstract RegularFileProperty getOutput();

    /**
     * Sets the single configuration (by name) to generate the SBOM for.
     *
     * @since 9.10.0
     */
    @Option(option = "configuration", description = "The configuration to generate the SBOM for.")
    public void setConfiguration(String configurationName) {
        getConfigurations().set(Collections.singletonList(configurationName));
    }

    @TaskAction
    public void generate() throws IOException {
        Graph graph = this.graph.get();
        try (JsonWriter json = new JsonWriter(Files.newBufferedWriter(getOutput().get().getAsFile().toPath(), StandardCharsets.UTF_8))) {
            json.setIndent("  ");
            json.beginObject();
            writeMetadata(json);
            json.name("nodeList");
            json.beginObject();
            writeNodes(json, graph.nodes);
            writeEdges(json, graph.edges);
            json.name("rootElements");
            json.beginArray();
            for (String root : graph.roots) {
                json.value(root);
            }
            json.endArray();
            json.endObject();
            json.endObject();
        }
    }

    private Graph collectGraph() {
        Graph result = new Graph();
        for (String name : getConfigurations().get()) {
            Configuration configuration = ConfigurationFinder.find(getProject().getConfigurations(), name);
            ResolvedComponentResult root = configuration.getIncoming().getResolutionResult().getRootComponent().get();
            result.addComponent(root.getId());
            result.roots.add(root.getId().getDisplayName());
            collectFrom(result, root);
        }
        return result;
    }

    private static void collectFrom(Graph result, ResolvedComponentResult from) {
        String fromId = from.getId().getDisplayName();
        for (DependencyResult dependency : from.getDependencies()) {
            if (!(dependency instanceof ResolvedDependencyResult) || dependency.isConstraint()) {
                continue;
            }
            ResolvedComponentResult selected = ((ResolvedDependencyResult) dependency).getSelected();
            boolean isNew = result.addComponent(selected.getId());
            result.edges.computeIfAbsent(fromId, k -> new TreeSet<>()).add(selected.getId().getDisplayName());
            if (isNew) {
                collectFrom(result, selected);
            }
        }
    }

    private static void writeMetadata(JsonWriter json) throws IOException {
        json.name("metadata");
        json.beginObject();
        json.name("name").value("Gradle");
        json.name("date").value(timestamp());
        json.name("tools");
        json.beginArray();
        json.beginObject();
        json.name("name").value("Gradle");
        json.name("version").value(GradleVersion.current().getVersion());
        json.endObject();
        json.endArray();
        json.name("authors");
        json.beginArray();
        json.beginObject();
        json.name("name").value("Gradle");
        json.name("isOrg").value(true);
        json.endObject();
        json.endArray();
        json.endObject();
    }

    private static void writeNodes(JsonWriter json, Map<String, Node> nodes) throws IOException {
        json.name("nodes");
        json.beginArray();
        for (Node node : nodes.values()) {
            json.beginObject();
            json.name("id").value(node.id);
            json.name("name").value(node.name);
            if (node.version != null) {
                json.name("version").value(node.version);
            }
            if (node.purl != null) {
                json.name("identifiers");
                json.beginObject();
                json.name("PURL").value(node.purl);
                json.endObject();
            }
            json.endObject();
        }
        json.endArray();
    }

    private static void writeEdges(JsonWriter json, Map<String, Set<String>> edges) throws IOException {
        json.name("edges");
        json.beginArray();
        for (Map.Entry<String, Set<String>> edge : edges.entrySet()) {
            json.beginObject();
            json.name("type").value("dependsOn");
            json.name("from").value(edge.getKey());
            json.name("to");
            json.beginArray();
            for (String to : edge.getValue()) {
                json.value(to);
            }
            json.endArray();
            json.endObject();
        }
        json.endArray();
    }

    /**
     * Honors {@code SOURCE_DATE_EPOCH} so that SBOMs diff cleanly in CI.
     */
    private static String timestamp() {
        String epoch = System.getenv("SOURCE_DATE_EPOCH");
        if (epoch != null) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(epoch)).toString();
            } catch (NumberFormatException ignored) {
                // fall through to the wall clock
            }
        }
        return Instant.now().toString();
    }

    private static final class Graph {
        private final TreeMap<String, Node> nodes = new TreeMap<>();
        private final TreeMap<String, Set<String>> edges = new TreeMap<>();
        private final TreeSet<String> roots = new TreeSet<>();

        private boolean addComponent(ComponentIdentifier id) {
            String displayName = id.getDisplayName();
            if (nodes.containsKey(displayName)) {
                return false;
            }
            if (id instanceof ModuleComponentIdentifier) {
                ModuleComponentIdentifier module = (ModuleComponentIdentifier) id;
                nodes.put(displayName, new Node(displayName, module.getModule(), module.getVersion(),
                    "pkg:maven/" + module.getGroup() + "/" + module.getModule() + "@" + module.getVersion()));
            } else {
                nodes.put(displayName, new Node(displayName, displayName, null, null));
            }
            return true;
        }
    }

    private static final class Node {
        private final String id;
        private final String name;
        @Nullable
        private final String version;
        @Nullable
        private final String purl;

        private Node(String id, String name, @Nullable String version, @Nullable String purl) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.purl = purl;
        }
    }
}
