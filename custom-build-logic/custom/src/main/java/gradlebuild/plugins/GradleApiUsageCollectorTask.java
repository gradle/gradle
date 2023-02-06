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

package gradlebuild.plugins;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import gradlebuild.AbstractClassAnalysisTask;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class GradleApiUsageCollectorTask extends AbstractClassAnalysisTask {

    @Input
    abstract Property<String> getPluginName();

    @OutputFile
    abstract RegularFileProperty getOutputFile();

    public GradleApiUsageCollectorTask() {
        getOutputFile().convention(getProject().getLayout().getBuildDirectory().file(
            getPluginName().map(name -> "plugin-analysis/" + name + ".analysis.json")
        ));
    }

    @Override
    protected void processClasses(Map<String, ClassData> classes) {

        Map<ComponentIdentifier, List<ClassGradleApiUsageResults>> filtered = classes.values().stream()
            .map(entry -> {
                Set<String> dependencies = entry.getDependencyClasses().stream()
                    .filter(dep -> dep.startsWith("org.gradle"))
                    .collect(Collectors.toSet());

                return new ClassData(entry.getName(), entry.getComponentId(), dependencies);
            })
            .filter(entry -> !entry.getDependencyClasses().isEmpty())
            .collect(Collectors.groupingBy(
                x -> x.getComponentId(),
                Collectors.mapping(x -> new ClassGradleApiUsageResults(x.getName(), x.getDependencyClasses()), Collectors.toList())
            ));

        // TODO: Reformat JSON to use gradle API class as key?
        writeJsonContent(new PluginGradleApiUsageResults(getPluginName().get(),
            filtered.entrySet().stream()
                .map(x -> new ComponentGradleApiUsageResults(x.getKey().toString(), x.getValue()))
                .collect(Collectors.toList())
        ));




//        Map<String, Set<String>> pluginClassesWithInternalDependencies =
//            filtered.entrySet().stream().map(entry ->
//                new AbstractMap.SimpleEntry<>(
//                    entry.getKey(),
//                    new TreeSet<>(entry.getValue().getDependencyClasses().stream()
//                        .filter(name -> name.contains("internal"))
//                        .collect(Collectors.toSet())
//                    )
//                ))
//                .filter(entry -> !entry.getValue().isEmpty())
//                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

//        Set<String> allDependencyClasses =
//            filtered.values().stream()
//                .flatMap(x -> x.getDependencyClasses().stream())
//                .collect(Collectors.toSet());
//        System.out.println("All dependencies: ");
//        for (String name : new TreeSet<>(allDependencyClasses)) {
//            System.out.println(name);
//        }


//        System.out.println("Internal dependencies:");
//        for (Map.Entry<String, Set<String>> entry : new TreeMap<>(pluginClassesWithInternalDependencies).entrySet()) {
//            System.out.println(entry.getKey());
//            entry.getValue().forEach(internalDep ->
//                System.out.println("\t" + internalDep)
//            );
//        }

    }

    private void writeJsonContent(PluginGradleApiUsageResults results) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(getOutputFile().getAsFile().get(), results);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class PluginGradleApiUsageResults {
        private final String pluginName;
        private final List<ComponentGradleApiUsageResults> components;

        public PluginGradleApiUsageResults(
            @JsonProperty("pluginName") String pluginName,
            @JsonProperty("components") List<ComponentGradleApiUsageResults> components
        ) {
            this.pluginName = pluginName;
            this.components = components;
        }

        public String getPluginName() {
            return pluginName;
        }

        public List<ComponentGradleApiUsageResults> getComponents() {
            return components;
        }
    }

    public static class ComponentGradleApiUsageResults {
        private final String componentId;
        private final List<ClassGradleApiUsageResults> classes;

        public ComponentGradleApiUsageResults(
            @JsonProperty("componentId") String componentId,
            @JsonProperty("classes") List<ClassGradleApiUsageResults> classes
        ) {
            this.componentId = componentId;
            this.classes = classes;
        }

        public String getComponentId() {
            return componentId;
        }

        public List<ClassGradleApiUsageResults> getClasses() {
            return classes;
        }
    }

    public static class ClassGradleApiUsageResults {
        private final String name;
        private final Set<String> dependencyClasses;

        public ClassGradleApiUsageResults(
            @JsonProperty("name") String name,
            @JsonProperty("dependencyClasses") Set<String> dependencyClasses
        ) {
            this.name = name;
            this.dependencyClasses = dependencyClasses;
        }

        public String getName() {
            return name;
        }

        public Set<String> getDependencyClasses() {
            return dependencyClasses;
        }
    }
}
