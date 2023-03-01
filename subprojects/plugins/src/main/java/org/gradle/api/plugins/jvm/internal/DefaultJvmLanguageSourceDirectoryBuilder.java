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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.JvmConstants;
import org.gradle.api.internal.tasks.compile.HasCompileOptions;
import org.gradle.api.plugins.internal.JvmPluginsHelper;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.JavaCompile;

import javax.inject.Inject;
import java.util.function.Function;

public class DefaultJvmLanguageSourceDirectoryBuilder implements JvmLanguageSourceDirectoryBuilder {
    private final String name;
    private final ProjectInternal project;
    private final SourceSet sourceSet;

    private String description;
    private Function<DirectoryProperty, TaskProvider<? extends AbstractCompile>> taskBuilder;
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
    public JvmLanguageSourceDirectoryBuilder compiledBy(Function<DirectoryProperty, TaskProvider<? extends AbstractCompile>> taskBuilder) {
        this.taskBuilder = taskBuilder;
        return this;
    }

    @Override
    public JvmLanguageSourceDirectoryBuilder compiledWithJava(Action<? super JavaCompile> compilerConfiguration) {
        return includeInAllJava().compiledBy(sourceDirectory ->
            project.getTasks().register("compile" + StringUtils.capitalize(name), JavaCompile.class, compileTask -> {
                compileTask.source(sourceDirectory);
                compileTask.setClasspath(sourceSet.getCompileClasspath());
                compilerConfiguration.execute(compileTask);
            })
        );
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

        TaskProvider<? extends AbstractCompile> compileTask = taskBuilder.apply(
            project.getObjects().directoryProperty().fileProvider(
                project.getProviders().provider(() -> langSrcDir.getSourceDirectories().getSingleFile())
            )
        );

        JvmPluginsHelper.configureOutputDirectoryForSourceSet(
            sourceSet,
            langSrcDir,
            project,
            compileTask,
            compileTask.map(task -> {
                if (task instanceof HasCompileOptions) {
                    return ((HasCompileOptions) task).getOptions();
                }
                throw new UnsupportedOperationException("Unsupported compile task " + task.getClass().getName());
            })
        );
        if (includeInAllJava) {
            sourceSet.getAllJava().source(langSrcDir);
        }
        sourceSet.getAllSource().source(langSrcDir);
        project.getTasks().named(JvmConstants.CLASSES_TASK_NAME).configure(classes ->
            classes.dependsOn(compileTask));
    }
}
