/*
 * Copyright 2022 the original author or authors.
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

package gradlebuild;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.serialize.InputStreamBackedDecoder;
import org.gradle.util.internal.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public abstract class D3GraphWriterTask extends DefaultTask {

    private static final String GRADLE_VERSION = "8.0";

    private static final Logger LOGGER = LoggerFactory.getLogger(D3GraphWriterTask.class);

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    public D3GraphWriterTask() {
        getOutputFile().convention(getProject().getLayout().getBuildDirectory().file("force-graph.json"));
    }

    private static final List<String> usedInWorkers = List.of(
        "base-annotations", "base-services", "build-operations", "build-option",
        "cli", "enterprise-logging", "enterprise-workers", "files-temp", "files", "hashing",
        "logging-api", "logging", "messaging", "native", "process-services", "testing-base",
        "testing-jvm-infrastructure", "worker-processes", "worker-services", "wrapper-shared", "wrapper");

    @InputFiles
    public abstract Property<FileCollection> getAnalyzedClasspath();

    @TaskAction
    public void run() {

//        Map<File, AnalyzingArtifactTransform.DependencyAnalysis> data = new TreeMap<>();
//        getAnalyzedClasspath().get().getIncoming().artifactView(configuration -> {}).getArtifacts().getArtifacts().forEach((ResolvedArtifactResult artifact) -> {
//            File file = artifact.getFile();

        // Map from a class name to the artifact it is defined in.
        Map<String, File> artifactMap = new TreeMap<>();

        // A map from a given class to all the classes it depends on.
        Map<String, ClassData> dependencyMap = new TreeMap<>();

        // A map from a given class to all the that which depend on it.
        Map<String, Set<String>> dependentMap = new TreeMap<>();

        getAnalyzedClasspath().get().getFiles().forEach(file -> {
            AnalyzingArtifactTransform.DependencyAnalysis analysis;
            try {
                AnalyzingArtifactTransform.DependencyAnalysis.Serializer serializer =
                    new AnalyzingArtifactTransform.DependencyAnalysis.Serializer(new StringInterner());
                analysis = serializer.read(new InputStreamBackedDecoder(Files.newInputStream(file.toPath())));
            } catch (Exception e) {
                throw new GradleException("Error reading transformed dependency analysis for: " + file.getName(), e);
            }

            analysis.getAnalyses().forEach(classAnalysis -> {
                Set<String> dependencyClasses = new TreeSet<>(classAnalysis.getPrivateClassDependencies());
                dependencyClasses.addAll(classAnalysis.getAccessibleClassDependencies());

                String className = classAnalysis.getClassName();
                ClassData classData = new ClassData();
                classData.dependencyClasses = dependencyClasses;
                classData.artifact = file;

                ClassData oldData;
                if ((oldData = dependencyMap.put(className, classData)) != null) {
                    LOGGER.warn("Duplicate: " + className);
                    LOGGER.warn("\t" + file + " " + oldData.artifact);
                } else {
                    // Only track the artifact of the first time we see a class
                    artifactMap.put(className, file);
                }

                dependencyClasses.forEach(dependent -> {
                    dependentMap.computeIfAbsent(dependent, key -> new TreeSet<>()).add(className);
                });
            });
        });

        List<String> IGNORED_CLASSES = List.of(
            "javax.annotation.Nullable", "org.gradle.api.Action", "org.gradle.api.Transformer",
            "org.gradle.api.specs.Spec", "org.gradle.internal.Cast", "org.slf4j.Logger",
            "org.gradle.api.Incubating", "org.gradle.api.GradleException", "org.gradle.api.provider.Provider",
            "org.gradle.internal.service.scopes.Scope", "org.slf4j.LoggerFactory",
            "org.gradle.internal.deprecation.DeprecationLogger");

        // Graph to plot.
        HashMap<String, ClassData> nodeMap = new HashMap<>(dependencyMap);

        // We only care to plot nodes that the production code depends on directly.
        nodeMap.entrySet().removeIf(entry -> {
            Set<String> dependents = dependentMap.get(entry.getKey());
            if (dependents == null) {
                return true; // Nobody depends on this. Don't print it.
            }
            return dependents.stream().noneMatch(dependent -> isPrimaryArtifact(artifactMap.get(dependent)));
        });



        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("\"nodes\": [\n");

        Iterator<Map.Entry<String, ClassData>> it = nodeMap.entrySet().iterator();
        while(it.hasNext()) {
            Map.Entry<String, ClassData> entry = it.next();
            String group = entry.getValue().artifact == null ? "unknown" : entry.getValue().artifact.getName();

            int num = group.hashCode();
            String color = String.format("hsl(%d, 100%%, 50%%)", Math.abs(num) % 360);
            String node = String.format("{'id': '%s', 'group':'%s', 'color2':'%s', 'val': '%d'}", entry.getKey(), group, color, (int)Math.sqrt(entry.getValue().dependencyClasses.size()));

            sb.append(node.replace('\'', '"'));
            if (it.hasNext()) {
                sb.append(",\n");
            }
        }

        sb.append("], \"links\": [\n");

        String links = nodeMap.entrySet().stream().flatMap(entry -> entry.getValue().dependencyClasses.stream()
            .filter(x -> !IGNORED_CLASSES.contains(x))
            .filter(nodeMap::containsKey)
            .map(target -> {
                String link = String.format("{'source': '%s', 'target': '%s'}", entry.getKey(), target);
                return link.replace('\'', '"');
            }
            )).collect(Collectors.joining(",\n"));
        sb.append(links);

        sb.append("]\n");
        sb.append("}\n");

        String output = sb.toString();
        try {
            Files.newOutputStream(getOutputFile().get().getAsFile().toPath()).write(output.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isPrimaryArtifact(File f) {
        if (f == null) {
            // It was a missing dependency. Maybe we care about it?
            // Really, this should not happen. At least how we're using this method now.
            return true;
        }


        String fileName = f.getName();
        return fileName.startsWith("gradle-");
    }

    public String getNodeColor(File node, Map<File, Set<String>> referencedByArtifact) {
        String module = getNodeModule(node);
        String color;
        if (getCount(node, referencedByArtifact) > 0) {
            color = usedInWorkers.contains(module) ? "green" : "red";
        } else {
            color = usedInWorkers.contains(module) ? "blue" : "pink";
        }
        return color;
    }

    public String getNodeModule(File file) {
        String fileName = file.getName();
        try
        {
            return fileName.substring("gradle-".length(), fileName.indexOf("-" + GRADLE_VERSION + ".jar.analysis"));
        } catch (Exception e) {
            throw new GradleException("Failed to find module of: " + fileName);
        }
    }

    public int getCount(File node, Map<File, Set<String>> referencedByArtifact) {
        return GUtil.elvis(referencedByArtifact.get(node), Collections.emptyList()).size();
    }

    private Set<String> findAllReferencedClasses(Map<String, ClassData> dependencyMap, Set<String> rootClasses) {
        Deque<String> toProcess = new ArrayDeque<>();
        Set<String> seen = new TreeSet<>();

        Set<String> missing = new TreeSet<>();

        rootClasses.forEach(root -> {
            toProcess.add(root);
            seen.add(root);
        });

        String next;
        while ((next = toProcess.poll()) != null) {

            @Nullable
            ClassData data = dependencyMap.get(next);

            if (data != null) {
                Set<String> usedClasses = data.dependencyClasses;
                for (String dependency : usedClasses) {
                    if (!seen.contains(dependency)) {
                        toProcess.add(dependency);
                        seen.add(dependency);
                    }
                }
            } else {
                missing.add(next);
            }
        }

        if (!missing.isEmpty()) {
            LOGGER.warn("Found missing classes: {}", missing.stream().collect(Collectors.joining("\n")));
        }

        seen.removeAll(missing);
        return seen;
    }

    private static class ClassData {
        File artifact;
        Set<String> dependencyClasses;
        public ClassData() {

        }
        public ClassData(File artifact, Set<String> dependencyClasses) {
            this.artifact = artifact;
            this.dependencyClasses = dependencyClasses;
        }
    }
}
