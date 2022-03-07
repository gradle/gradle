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
package org.gradle.api.plugins.jvm.internal;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultSourceSetOutput;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.Cast;

import javax.inject.Inject;
import java.util.function.Function;

public class DefaultJvmLanguageGeneratedSourceDirectoryBuilder implements JvmLanguageGeneratedSourceDirectoryBuilder {
    private final ProjectInternal project;
    private final SourceSet sourceSet;

    private String description;
    private Action<? super CompileTaskDetails> taskBuilder;
    private TaskProvider<? extends Task> sourceTaskProvider;
    private Function<? extends Task, DirectoryProperty> mapping;
    private boolean includeInAllJava;

    @Inject
    public DefaultJvmLanguageGeneratedSourceDirectoryBuilder(ProjectInternal project, SourceSet sourceSet) {
        this.project = project;
        this.sourceSet = sourceSet;
    }

    @Override
    public JvmLanguageSourceDirectoryBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    @Override
    public JvmLanguageSourceDirectoryBuilder compiledBy(Action<? super CompileTaskDetails> taskBuilder) {
        this.taskBuilder = taskBuilder;
        return this;
    }

    @Override
    public JvmLanguageSourceDirectoryBuilder compiledWithJava(Action<? super JavaCompile> compilerConfiguration) {
        includeInAllJava();
        return doCompileWithJava(compilerConfiguration);
    }

    private JvmLanguageSourceDirectoryBuilder doCompileWithJava(Action<? super JavaCompile> compilerConfiguration) {
        return compiledBy(details -> {
            String compileTaskName = "compile" + StringUtils.capitalize(sourceTaskProvider.getName());
            TaskProvider<JavaCompile> taskProvider = project.getTasks().register(compileTaskName, JavaCompile.class, compileTask -> {
                compileTask.setDescription("Compile task for " + description);
                compileTask.source(details.getSourceDirectory());
                compileTask.setClasspath(sourceSet.getCompileClasspath());
                compileTask.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir(
                    "classes/" + compileTaskName + "/" + sourceSet.getName()
                ));
                compilerConfiguration.execute(compileTask);
            });
            details.setCompileTask(taskProvider, JavaCompile::getDestinationDirectory);
        });
    }

    @Override
    public JvmLanguageSourceDirectoryBuilder includeInAllJava() {
        includeInAllJava = true;
        return this;
    }

    void build() {
        if (taskBuilder == null) {
            throw new IllegalStateException("You must specify how sources will be compiled either by calling compiledWithJava or compiledBy");
        }
        if (mapping == null) {
            throw new IllegalStateException("You must specify the mapping function from your source generating task to a directory property");
        }
        if (sourceTaskProvider == null) {
            throw new IllegalStateException("You must specify the source generation task");
        }
        Provider<Directory> sourceDirectory = sourceTaskProvider.flatMap(task -> mapping.apply(Cast.uncheckedCast(task)));
        DefaultCompileTaskDetails details = createTaskDetails(
            project.getObjects().directoryProperty().convention(sourceDirectory)
        );
        DefaultSourceSetOutput sourceSetOutput = Cast.cast(DefaultSourceSetOutput.class, sourceSet.getOutput());
        sourceSetOutput.addClassesDir(details.compileTask.flatMap(task -> details.compileMapping.apply(Cast.uncheckedCast(task))));
        sourceSetOutput.registerClassesContributor(details.compileTask);
        sourceSetOutput.getGeneratedSourcesDirs().from(sourceDirectory);
        project.getTasks().matching(DefaultJvmLanguageGeneratedSourceDirectoryBuilder::isClassesTask).configureEach(classes ->
            classes.dependsOn(details.compileTask));
        if (includeInAllJava) {
            sourceSet.getAllJava().srcDir(sourceDirectory);
        }
        sourceSet.getAllSource().srcDir(sourceDirectory);
    }

    private static boolean isClassesTask(Task t) {
        return JavaPlugin.CLASSES_TASK_NAME.equals(t.getName());
    }

    private DefaultCompileTaskDetails createTaskDetails(DirectoryProperty sources) {
        DefaultCompileTaskDetails details = new DefaultCompileTaskDetails(sources);
        taskBuilder.execute(details);
        return details;
    }

    @Override
    public <T extends Task> JvmLanguageGeneratedSourceDirectoryBuilder forSourceGeneratingTask(TaskProvider<T> taskProvider, Function<T, DirectoryProperty> mapping) {
        this.sourceTaskProvider = taskProvider;
        this.mapping = mapping;
        return this;
    }

    private static class DefaultCompileTaskDetails implements CompileTaskDetails {
        private final DirectoryProperty sourceDirectory;

        private TaskProvider<? extends Task> compileTask;
        private Function<? extends Task, DirectoryProperty> compileMapping;

        public DefaultCompileTaskDetails(DirectoryProperty sourceDirectory) {
            this.sourceDirectory = sourceDirectory;
        }

        @Override
        public DirectoryProperty getSourceDirectory() {
            return sourceDirectory;
        }

        @Override
        public <T extends Task> void setCompileTask(TaskProvider<? extends Task> task, Function<T, DirectoryProperty> mapping) {
            this.compileTask = task;
            this.compileMapping = mapping;
        }
    }
}
