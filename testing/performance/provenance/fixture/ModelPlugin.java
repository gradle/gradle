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

package example;

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;

/** Ordinary project properties, conventions, replacement, chains and finalization. */
public class ModelPlugin implements Plugin<Project> {
    public static class Model {
        public final Property<String> source;
        public final Property<String> normalized;
        public final Property<String> selected;

        public Model(Project project) {
            source = project.getObjects().property(String.class).convention("default");
            source.set(project.getProviders().gradleProperty("model-value").orElse("configured"));
            normalized = project.getObjects().property(String.class).convention(source.map(String::trim));
            selected = project.getObjects().property(String.class).convention("fallback");
            selected.set(normalized);
        }
    }

    public abstract static class ModelTask extends DefaultTask {
        @Internal
        public abstract Property<String> getSource();

        @Internal
        public abstract Property<String> getSelected();

        @Internal
        public abstract Property<String> getFinalized();

        @Internal
        public abstract Property<String> getReplaced();
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("java-library");
        Model model = new Model(project);
        project.getExtensions().add("sampleModel", model);
        int taskCount = Integer.parseInt(project.findProperty("sampleTasks").toString());
        for (int i = 0; i < taskCount; i++) {
            project.getTasks().register("model" + i, ModelTask.class, task -> {
                task.getSource().convention(model.source);
                task.getSelected().convention("fallback");
                task.getSelected().set(model.selected);
                task.getFinalized().set(task.getSelected());
                task.getFinalized().finalizeValue();
                task.getReplaced().convention("fallback");
                task.getReplaced().set("superseded");
                task.getReplaced().set(model.normalized);
            });
        }
        // Realize these properties before the checkpoint and keep the tasks rooted in the model.
        project.getTasks().register("modelCheckpoint", task -> task.dependsOn(project.getTasks().withType(ModelTask.class)));
    }
}
