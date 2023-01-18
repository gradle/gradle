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

package gradlebuild;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.serialize.InputStreamBackedDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public abstract class RawJsonWriterTask extends DefaultTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(RawJsonWriterTask.class);

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    public RawJsonWriterTask() {
        getOutputFile().convention(getProject().getLayout().getBuildDirectory().file("class-analysis.json"));
    }

    @InputFiles
    public abstract ConfigurableFileCollection getAnalyzedClasspath();

    @Input
    public abstract ListProperty<ComponentArtifactIdentifier> getArtifactIdentifiers();

    private Map<ComponentIdentifier, File> filesByIdentifiers() {
        Map<ComponentIdentifier, File> map = new HashMap<>();
        List<ComponentArtifactIdentifier> identifiers = getArtifactIdentifiers().get();
        List<File> files = new ArrayList<>(getAnalyzedClasspath().getFiles());
        for (int index = 0; index < identifiers.size(); index++) {
            map.put(identifiers.get(index).getComponentIdentifier(), files.get(index));
        }
        return map;
    }

    @TaskAction
    public void run() {

        // A map from a given class to all the classes it depends on.
        Map<String, ClassData> dependencyMap = new TreeMap<>();

        filesByIdentifiers().forEach((id, file) -> {
            AnalyzingArtifactTransform.DependencyAnalysis analysis;
            try {
                AnalyzingArtifactTransform.DependencyAnalysis.Serializer serializer =
                    new AnalyzingArtifactTransform.DependencyAnalysis.Serializer(new StringInterner());
                analysis = serializer.read(new InputStreamBackedDecoder(Files.newInputStream(file.toPath())));
            } catch (Exception e) {
                throw new GradleException("Error reading transformed dependency analysis for: " + file.getName(), e);
            }

            analysis.getClasses().forEach(classAnalysis -> {
                Set<String> dependencyClasses = new TreeSet<>(classAnalysis.getPrivateClassDependencies());
                dependencyClasses.addAll(classAnalysis.getAccessibleClassDependencies());

                String className = classAnalysis.getClassName();
                ClassData classData = new ClassData(className, id, dependencyClasses);

                ClassData oldData;
                if ((oldData = dependencyMap.put(className, classData)) != null) {
                    LOGGER.warn("Duplicate: " + className);
                    LOGGER.warn("\t" + id + " " + oldData.componentId);
                }
            });
        });

        Map<String, ClassData> gradleClasses = dependencyMap.entrySet().stream()
            .filter(x -> x.getValue().componentId instanceof ProjectComponentIdentifier)
            .collect(Collectors.toMap(x -> x.getKey(), x -> {
                Set<String> dependencies = x.getValue().dependencyClasses.stream().filter(dep -> {
                    ClassData dependency = dependencyMap.get(dep);
                    return dependency != null && dependency.componentId instanceof  ProjectComponentIdentifier;
                }).collect(Collectors.toSet());
                return new ClassData(x.getValue().name, x.getValue().componentId, dependencies);
            }));

        renderNodes(new TreeMap<>(gradleClasses));
    }

    private void renderNodes(Map<String, ClassData> nodeMap) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");

        Iterator<Map.Entry<String, ClassData>> it = nodeMap.entrySet().iterator();
        while(it.hasNext()) {
            Map.Entry<String, ClassData> entry = it.next();


            String dependencies = "[\"" + String.join("\", \"", entry.getValue().dependencyClasses) + "\"]";


            String artifact = ((ProjectComponentIdentifier) entry.getValue().componentId).getProjectName();
            String node = String.format("{\"name\": \"%s\", \"project\":\"%s\", \"dependencies\": %s}", entry.getValue().name, artifact, dependencies);
            sb.append(node);

            if (it.hasNext()) {
                sb.append(",\n");
            }
        }

        sb.append("]\n");

        String output = sb.toString();
        try {
            Files.newOutputStream(getOutputFile().get().getAsFile().toPath()).write(output.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class ClassData {
        private final String name;
        private final ComponentIdentifier componentId;
        private final Set<String> dependencyClasses;

        public ClassData(String name, ComponentIdentifier componentId, Set<String> dependencyClasses) {
            this.componentId = componentId;
            this.name = name;
            this.dependencyClasses = dependencyClasses;
        }
    }
}
