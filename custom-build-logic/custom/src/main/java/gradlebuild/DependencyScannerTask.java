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
import org.gradle.api.file.FileSystemLocation;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class DependencyScannerTask extends DefaultTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyScannerTask.class);

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    public DependencyScannerTask() {
        getOutputFile().set(getProject().getLayout().getBuildDirectory().file("graphviz"));
    }

    private static final List<String> usedInWorkers = List.of(
        "base-annotations", "base-services", "build-operations", "build-option",
        "cli", "enterprise-logging", "enterprise-workers", "files-temp", "files", "hashing",
        "logging-api", "logging", "messaging", "native", "process-services", "testing-base",
        "testing-jvm", "worker-processes", "worker-services", "wrapper-shared", "wrapper");

    @InputFiles
    public abstract Property<FileCollection> getAnalyzedClasspath();

    @TaskAction
    public void run() {

        Map<File, AnalyzingArtifactTransform.DependencyAnalysis> data = new TreeMap<>();
//        getAnalyzedClasspath().get().getIncoming().artifactView(configuration -> {}).getArtifacts().getArtifacts().forEach((ResolvedArtifactResult artifact) -> {
//            File file = artifact.getFile();
        getAnalyzedClasspath().get().getFiles().forEach(file -> {
            AnalyzingArtifactTransform.DependencyAnalysis analysis;
            try {
                AnalyzingArtifactTransform.DependencyAnalysis.Serializer serializer =
                    new AnalyzingArtifactTransform.DependencyAnalysis.Serializer(new StringInterner());
                analysis = serializer.read(new InputStreamBackedDecoder(Files.newInputStream(file.toPath())));
            } catch (Exception e) {
                throw new GradleException("Error reading transformed dependency analysis for: " + file.getName(), e);
            }

            // SHOUILD GO HERE VVVVV
            if (data.put(file, analysis) != null) {
                throw new RuntimeException("Oh no!");
            }
        });

        // Map from a class name to the artifact it is defined in.
        Map<String, File> artifactMap = new TreeMap<>();

        // A map from a given class to all the classes it depends on.
        Map<String, ClassData> dependencyMap = new TreeMap<>();
        Set<String> duplicateClasses = new TreeSet<>();
        data.forEach((artifact, depAnalysis) -> {

            // THIS CODE ^^^
            depAnalysis.getAnalyses().forEach(classAnalysis -> {
                Set<String> dependencyClasses = new TreeSet<>(classAnalysis.getPrivateClassDependencies());
                dependencyClasses.addAll(classAnalysis.getAccessibleClassDependencies());

                String className = classAnalysis.getClassName();
                ClassData classData = new ClassData();
                classData.dependencyClasses = dependencyClasses;
                classData.artifact = artifact;

                if (dependencyMap.put(className, classData) != null) {
                    duplicateClasses.add(className);
                    LOGGER.info("Found duplicate class. Name: " + className);
                } else {
                    // Only track the artifact of the first time we see a class
                    artifactMap.put(className, artifact);
                }
            });
        });

//        dependencyMap.get("org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework$TestClassProcessorFactoryImpl").dependencyClasses
//            .remove("org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework");
//        dependencyMap.get("org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework$TestClassProcessorFactoryImpl").dependencyClasses
//            .remove("org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework");
//        dependencyMap.get("org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework$JUnitPlatformTestClassProcessorFactory").dependencyClasses
//            .remove("org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework");

        Map<String, Set<String>> reverseDependencyMap = new TreeMap<>();
        dependencyMap.forEach((k, v) -> v.dependencyClasses.forEach(dep ->
            reverseDependencyMap.computeIfAbsent(dep, d -> new TreeSet<>()).add(k)));

        // Also WorkerTestClassProcessorFactory
        Set<String> rootClasses = Stream.of(
            "org.gradle.process.internal.worker.GradleWorkerMain",
            "org.gradle.process.internal.worker.child.SystemApplicationClassLoaderWorker",
            "org.gradle.api.internal.tasks.testing.worker.TestWorker",
            "org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestClassProcessorFactory",
            "org.gradle.api.internal.tasks.testing.junit.TestClassProcessorFactoryImpl",
            "org.gradle.api.internal.tasks.testing.testng.TestClassProcessorFactoryImpl"
        ).collect(Collectors.toSet());

        Set<String> referencedClasses = findAllReferencedClasses(dependencyMap, rootClasses);

        List<String> unreferencedClasses = new ArrayList<>(dependencyMap.keySet());
        unreferencedClasses.removeAll(referencedClasses);

        // Find out which artifacts are and are not really used very much.
        Map<File, Set<String>> referencedByArtifact = new TreeMap<>();
        referencedClasses.forEach(referenced -> {
            File artifact = dependencyMap.get(referenced).artifact;
            referencedByArtifact.computeIfAbsent(artifact, it -> new TreeSet<>()).add(referenced);
        });

        Map<File, Set<String>> unreferencedByArtifact = new TreeMap<>();
        unreferencedClasses.forEach(unreferenced -> {
            File artifact = dependencyMap.get(unreferenced).artifact;
            unreferencedByArtifact.computeIfAbsent(artifact, it -> new TreeSet<>()).add(unreferenced);
        });

//        analyzed1(referencedByArtifact, unreferencedByArtifact);

        // Construct a graph of which artifacts reference which.
        // These are the edges between each artifact. Each edge contains a
        // list of classes which contribute to that edge.
        class ArtifactStats {
            Set<String> contributingClassesSource = new TreeSet<>();
            Set<String> contributingClassesDest = new TreeSet<>();
            int edges = 0;
        }
        Map<File, Map<File, ArtifactStats>> artifactGraph = new TreeMap<>();
        referencedClasses.forEach(referenced -> {
            ClassData classData = dependencyMap.get(referenced);
            File artifact = classData.artifact;
            classData.dependencyClasses.forEach(dependency -> {
                File dependencyArtifact = artifactMap.get(dependency);
                if (dependencyArtifact != null) {
                    if (!dependencyArtifact.equals(artifact)) {
                        ArtifactStats stats = artifactGraph.computeIfAbsent(artifact, it -> new TreeMap<>()).computeIfAbsent(dependencyArtifact, k -> new ArtifactStats());
                        stats.contributingClassesSource.add(referenced);
                        stats.contributingClassesDest.add(dependency);
                        stats.edges++;
                    }
                } else {
                    LOGGER.info("Missing artifact for dependency: " + dependency);
                }
            });
        });
        unreferencedClasses.forEach(referenced -> {
            ClassData classData = dependencyMap.get(referenced);
            File artifact = classData.artifact;
            classData.dependencyClasses.forEach(dependency -> {
                File dependencyArtifact = artifactMap.get(dependency);
                if (dependencyArtifact != null) {
                    if (!dependencyArtifact.equals(artifact)) {
                        ArtifactStats stats = artifactGraph.computeIfAbsent(artifact, it -> new TreeMap<>()).computeIfAbsent(dependencyArtifact, v -> new ArtifactStats());
//                        stats.contributingClassesSource.add(referenced);
//                        stats.contributingClassesDest.add(dependency);
                    }
                } else {
                    LOGGER.info("Missing artifact for dependency: " + dependency);
                }
            });
        });

        Comparator<File[]> edgeComparator = Comparator.comparing(pair -> pair[0].getName());
        edgeComparator = edgeComparator.thenComparing(Comparator.comparing(pair -> pair[1].getName()));

        String edges = artifactGraph.entrySet().stream()
            .filter(entry -> entry.getKey().getName().startsWith("gradle-"))
            .flatMap(entry ->
                entry.getValue().keySet().stream()
                    .filter(dependency -> dependency.getName().startsWith("gradle-"))
                    .map(dependency -> new File[]{entry.getKey(), dependency}))
            .sorted(edgeComparator)
//            .filter(pair -> getCount(pair[0], referencedByArtifact) != 0 )
            .map(pair -> {
                ArtifactStats stats = artifactGraph.get(pair[0]).get(pair[1]);
                String label = stats.edges + "/" + stats.contributingClassesSource.size() + "/" + stats.contributingClassesDest.size();
                String color = getNodeColor(pair[1], referencedByArtifact);
                return String.format("\"%s\" -> \"%s\" [label=\"%s\", color=%s]", pair[0].getName(), pair[1].getName(), label, color);
            }).collect(Collectors.joining("\n"));

        String nodes = artifactGraph.entrySet().stream()
            .filter(entry -> entry.getKey().getName().startsWith("gradle-"))
            .flatMap(entry -> Stream.concat(Stream.of(entry.getKey()), entry.getValue().keySet().stream()
                .filter(dependency -> dependency.getName().startsWith("gradle-"))
            ))
            .distinct()
            .sorted(Comparator.comparing(File::getName))
            .map((File file) -> {
                String fileName = file.getName();
                String module = fileName.substring("gradle-".length(), fileName.indexOf("-7.6.jar.analysis"));
                int count = getCount(file, referencedByArtifact);
                String label = String.format("%s (%d/%d)", module, count, getCount(file, unreferencedByArtifact));
                String color = getNodeColor(file, referencedByArtifact);

                return String.format("\"%s\" [label=\"%s\",fillcolor=%s]", fileName, label,color);
            }).collect(Collectors.joining("\n"));

        String output = String.format("digraph G {\nnode[shape=rect,style=filled]\n%s\n%s\n}", nodes, edges);
        System.err.println(output);
        try {
            Files.newOutputStream(getOutputFile().get().getAsFile().toPath()).write(output.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        return fileName.substring("gradle-".length(), fileName.indexOf("-7.6.jar.analysis"));
    }

    public int getCount(File node, Map<File, Set<String>> referencedByArtifact) {
        return GUtil.elvis(referencedByArtifact.get(node), Collections.emptyList()).size();
    }

    private void analyzed1(Map<File, Set<String>> referencedByArtifact, Map<File, Set<String>> unreferencedByArtifact) {
        String output = Stream.concat(referencedByArtifact.keySet().stream(), unreferencedByArtifact.keySet().stream())
            .distinct()
            .filter(it -> it.getName().startsWith("gradle-"))
            .sorted(
                               Comparator.comparingInt(it -> GUtil.elvis(referencedByArtifact.get(it), Collections.emptySet()).size())
                .thenComparing(Comparator.comparingInt(it -> GUtil.elvis(unreferencedByArtifact.get(it),  Collections.emptySet()).size()))
                .reversed())
            .map(it -> {
                Set<String> refClasses = GUtil.elvis(referencedByArtifact.get(it), Collections.emptySet());
                Set<String> unrefClasses = GUtil.elvis(unreferencedByArtifact.get(it), Collections.emptySet());

                Set<String> leastClasses = refClasses.size() < unrefClasses.size() ?
                    refClasses : unrefClasses;



                return
                    it.getName() + " " +
                        refClasses.size() + " " +
                        unrefClasses.size() + "" +
                        leastClasses.stream().map(it2 -> "\n\t" + it2).collect(Collectors.joining(""));
        }).collect(Collectors.joining("\n"));

        System.err.println(output);
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
                    }
                    seen.add(dependency);
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

    private class ClassData {
        File artifact;
        Set<String> dependencyClasses;
    }
}
