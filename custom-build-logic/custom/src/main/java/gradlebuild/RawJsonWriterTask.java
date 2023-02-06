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

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public abstract class RawJsonWriterTask extends AbstractClassAnalysisTask {

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    public RawJsonWriterTask() {
        getOutputFile().convention(getProject().getLayout().getBuildDirectory().file("class-analysis.json"));
    }

    @Override
    protected void processClasses(Map<String, ClassData> dependencyMap) {
        // Filter the entire dependency tree to only contain Gradle classes.
        Map<String, ClassData> gradleClasses = dependencyMap.entrySet().stream()
            .filter(x -> x.getValue().getComponentId() instanceof ProjectComponentIdentifier)
            .collect(Collectors.toMap(x -> x.getKey(), x -> {
                Set<String> dependencies = x.getValue().getDependencyClasses().stream().filter(dep -> {
                    ClassData dependency = dependencyMap.get(dep);
                    return dependency != null && dependency.getComponentId() instanceof  ProjectComponentIdentifier;
                }).collect(Collectors.toSet());
                return new ClassData(x.getValue().getName(), x.getValue().getComponentId(), dependencies);
            }));

        renderNodes(new TreeMap<>(gradleClasses));
    }

    private void renderNodes(Map<String, ClassData> nodeMap) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");

        Iterator<Map.Entry<String, ClassData>> it = nodeMap.entrySet().iterator();
        while(it.hasNext()) {
            Map.Entry<String, ClassData> entry = it.next();


            String dependencies = "[\"" + String.join("\", \"", entry.getValue().getDependencyClasses()) + "\"]";


            String artifact = ((ProjectComponentIdentifier) entry.getValue().getComponentId()).getProjectName();
            String node = String.format("{\"name\": \"%s\", \"project\":\"%s\", \"dependencies\": %s}", entry.getValue().getName(), artifact, dependencies);
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
}
