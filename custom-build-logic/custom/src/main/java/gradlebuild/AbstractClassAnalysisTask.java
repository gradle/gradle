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
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.serialize.InputStreamBackedDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public abstract class AbstractClassAnalysisTask extends DefaultTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractClassAnalysisTask.class);

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
//                    LOGGER.warn("Duplicate: " + className);
//                    LOGGER.warn("\t" + id + " " + oldData.componentId);
                }
            });
        });

        processClasses(dependencyMap);
    }

    protected abstract void processClasses(Map<String, ClassData> classes);

    protected static class ClassData {
        private final String name;
        private final ComponentIdentifier componentId;
        private final Set<String> dependencyClasses;

        public ClassData(String name, ComponentIdentifier componentId, Set<String> dependencyClasses) {
            this.componentId = componentId;
            this.name = name;
            this.dependencyClasses = dependencyClasses;
        }

        public String getName() {
            return name;
        }

        public ComponentIdentifier getComponentId() {
            return componentId;
        }

        public Set<String> getDependencyClasses() {
            return dependencyClasses;
        }
    }
}
