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
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.serialize.InputStreamBackedDecoder;
import org.gradle.util.internal.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class DependencyScannerTask extends DefaultTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyScannerTask.class);

    @InputFiles
    public abstract Property<FileCollection> getAnalyzedClasspath();

    @TaskAction
    public void run() {

        Map<File, AnalyzingArtifactTransform.DependencyAnalysis> data = new HashMap<>();
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

        // TODO: Need map pointing from class name to artifact.

        // A map from a given class to all the classes it depends on.
        Map<String, ClassData> dependencyMap = new HashMap<>();
        Set<String> duplicateClasses = new HashSet<>();
        data.forEach((artifact, depAnalysis) -> {

            // THIS CODE ^^^
            depAnalysis.getAnalyses().forEach(classAnalysis -> {
                Set<String> dependencyClasses = new HashSet<>(classAnalysis.getPrivateClassDependencies());
                dependencyClasses.addAll(classAnalysis.getAccessibleClassDependencies());

                String className = classAnalysis.getClassName();
                ClassData classData = new ClassData();
                classData.dependencyClasses = dependencyClasses;
                classData.artifact = artifact;

                if (dependencyMap.put(className, classData) != null) {
                    duplicateClasses.add(className);
                    LOGGER.info("Found duplicate class. Name: " + className);
                }
            });
        });

        Set<String> rootClasses = Stream.of(
            "org.gradle.process.internal.worker.GradleWorkerMain",
            "org.gradle.process.internal.worker.child.SystemApplicationClassLoaderWorker",
            "org.gradle.api.internal.tasks.testing.worker.TestWorker"
        ).collect(Collectors.toSet());

        Set<String> referencedClasses = findAllReferencedClasses(dependencyMap, rootClasses);

        List<String> unreferencedClasses = new ArrayList<>(dependencyMap.keySet());
        unreferencedClasses.removeAll(referencedClasses);

        // Find out which artifacts are and are not really used very much.
        Map<File, Set<String>> referencedByArtifact = new HashMap<>();
        referencedClasses.forEach(referenced -> {
            File artifact = dependencyMap.get(referenced).artifact;
            referencedByArtifact.computeIfAbsent(artifact, it -> new HashSet<>()).add(referenced);
        });

        Map<File, Set<String>> unreferencedByArtifact = new HashMap<>();
        unreferencedClasses.forEach(unreferenced -> {
            File artifact = dependencyMap.get(unreferenced).artifact;
            unreferencedByArtifact.computeIfAbsent(artifact, it -> new HashSet<>()).add(unreferenced);
        });

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
        Set<String> seen = new HashSet<>();

        Set<String> missing = new HashSet<>();

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
