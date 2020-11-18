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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.HasCompileOptions;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.internal.JvmPluginsHelper;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.Cast;

import javax.inject.Inject;
import java.util.function.Function;

public class DefaultJvmLanguageSourceDirectoryBuilder implements JvmLanguageSourceDirectoryBuilder {
    private final String name;
    private final ProjectInternal project;
    private final SourceSet sourceSet;

    private String description;
    private Action<? super CompileTaskDetails> taskBuilder;
    private boolean includeInAllJava;

    @Inject
    public DefaultJvmLanguageSourceDirectoryBuilder(String name, ProjectInternal project, SourceSet sourceSet) {
        this.name = name;
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
        return includeInAllJava().compiledBy(details -> {
            TaskProvider<JavaCompile> taskProvider = project.getTasks().register("compile" + StringUtils.capitalize(name), JavaCompile.class, compileTask -> {
                compileTask.source(details.getSourceDirectory());
                compileTask.setClasspath(sourceSet.getCompileClasspath());
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
            throw new IllegalStateException("You must specify the task which will contribute classes from this source directory");
        }
        SourceDirectorySet langSrcDir = project.getObjects().sourceDirectorySet(name, description == null ? "Sources for " + name : description);
        langSrcDir.srcDir("src/" + sourceSet.getName() + "/" + name);
        DefaultCompileTaskDetails details = createTaskDetails(langSrcDir);
        JvmPluginsHelper.configureOutputDirectoryForSourceSet(
            sourceSet,
            langSrcDir,
            project,
            details.task,
            details.task.map(task -> {
                if (task instanceof HasCompileOptions) {
                    return ((HasCompileOptions) task).getOptions();
                }
                throw new UnsupportedOperationException("Unsupported compile task " + task.getClass().getName());
            }),
            Cast.uncheckedCast(details.mapping)
        );
        if (includeInAllJava) {
            sourceSet.getAllJava().source(langSrcDir);
        }
        sourceSet.getAllSource().source(langSrcDir);
        project.getTasks().named(JavaPlugin.CLASSES_TASK_NAME).configure(classes ->
            classes.dependsOn(details.task));
    }

    private DefaultCompileTaskDetails createTaskDetails(SourceDirectorySet langSrcDir) {
        DefaultCompileTaskDetails details = new DefaultCompileTaskDetails(
            project.getObjects().directoryProperty().fileProvider(
                project.getProviders().provider(() -> langSrcDir.getSourceDirectories().getSingleFile())
            ));
        taskBuilder.execute(details);
        return details;
    }

    private static class DefaultCompileTaskDetails implements CompileTaskDetails {
        private final DirectoryProperty langSrcDir;
        private TaskProvider<? extends Task> task;
        private Function<? extends Task, DirectoryProperty> mapping;

        public DefaultCompileTaskDetails(DirectoryProperty langSrcDir) {
            this.langSrcDir = langSrcDir;
        }

        @Override
        public DirectoryProperty getSourceDirectory() {
            return langSrcDir;
        }

        @Override
        public <T extends Task> void setCompileTask(TaskProvider<? extends Task> task, Function<T, DirectoryProperty> mapping) {
            this.task = task;
            this.mapping = mapping;
        }
    }
}
