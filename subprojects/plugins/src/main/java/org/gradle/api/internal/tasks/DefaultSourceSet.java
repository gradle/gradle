/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks;

import groovy.lang.Closure;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.api.internal.jvm.ClassDirectoryBinaryNamingScheme;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.util.GUtil;

public class DefaultSourceSet implements SourceSet {
    private final String name;
    private FileCollection compileClasspath;
    private FileCollection runtimeClasspath;
    private final SourceDirectorySet javaSource;
    private final SourceDirectorySet allJavaSource;
    private final SourceDirectorySet resources;
    private final String displayName;
    private final SourceDirectorySet allSource;
    private final ClassDirectoryBinaryNamingScheme namingScheme;
    private DefaultSourceSetOutput output;

    public DefaultSourceSet(String name, SourceDirectorySetFactory sourceDirectorySetFactory) {
        this.name = name;
        displayName = GUtil.toWords(this.name);
        namingScheme = new ClassDirectoryBinaryNamingScheme(name);

        String javaSrcDisplayName = displayName + " Java source";

        javaSource = sourceDirectorySetFactory.create(javaSrcDisplayName);
        javaSource.getFilter().include("**/*.java");

        allJavaSource = sourceDirectorySetFactory.create(javaSrcDisplayName);
        allJavaSource.getFilter().include("**/*.java");
        allJavaSource.source(javaSource);

        String resourcesDisplayName = displayName + " resources";
        resources = sourceDirectorySetFactory.create(resourcesDisplayName);
        resources.getFilter().exclude(new Spec<FileTreeElement>() {
            public boolean isSatisfiedBy(FileTreeElement element) {
                return javaSource.contains(element.getFile());
            }
        });

        String allSourceDisplayName = displayName + " source";
        allSource = sourceDirectorySetFactory.create(allSourceDisplayName);
        allSource.source(resources);
        allSource.source(javaSource);
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "source set '" + getDisplayName() + "'";
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getClassesTaskName() {
        return getTaskName(null, "classes");
    }

    public String getCompileTaskName(String language) {
        return getTaskName("compile", language);
    }

    public String getCompileJavaTaskName() {
        return getCompileTaskName("java");
    }

    public String getProcessResourcesTaskName() {
        return getTaskName("process", "resources");
    }

    public String getJarTaskName() {
        return getTaskName(null, "jar");
    }

    public String getTaskName(String verb, String target) {
        return namingScheme.getTaskName(verb, target);
    }

    private String getTaskBaseName() {
        return name.equals(SourceSet.MAIN_SOURCE_SET_NAME) ? "" : GUtil.toCamelCase(name);
    }

    public String getCompileConfigurationName() {
        return StringUtils.uncapitalize(getTaskBaseName() + "Compile");
    }

    public String getRuntimeConfigurationName() {
        return StringUtils.uncapitalize(getTaskBaseName() + "Runtime");
    }

    public String getCompileOnlyConfigurationName() {
        return StringUtils.uncapitalize(getTaskBaseName() + "CompileOnly");
    }

    @Override
    public String getCompileClasspathConfigurationName() {
        return StringUtils.uncapitalize(getTaskBaseName() + "CompileClasspath");
    }

    public SourceSetOutput getOutput() {
        return output;
    }

    public void setClasses(DefaultSourceSetOutput classes) {
        this.output = classes;
    }

    public SourceSet compiledBy(Object... taskPaths) {
        output.builtBy(taskPaths);
        return this;
    }

    public FileCollection getCompileClasspath() {
        return compileClasspath;
    }

    public FileCollection getRuntimeClasspath() {
        return runtimeClasspath;
    }

    public void setCompileClasspath(FileCollection classpath) {
        compileClasspath = classpath;
    }

    public void setRuntimeClasspath(FileCollection classpath) {
        runtimeClasspath = classpath;
    }

    public SourceDirectorySet getJava() {
        return javaSource;
    }

    public SourceSet java(Closure configureClosure) {
        return java(ClosureBackedAction.of(configureClosure));
    }

    @Override
    public SourceSet java(Action<? super SourceDirectorySet> configureAction) {
        configureAction.execute(getJava());
        return this;
    }

    public SourceDirectorySet getAllJava() {
        return allJavaSource;
    }

    public SourceDirectorySet getResources() {
        return resources;
    }

    public SourceSet resources(Closure configureClosure) {
        return resources(ClosureBackedAction.of(configureClosure));
    }

    @Override
    public SourceSet resources(Action<? super SourceDirectorySet> configureAction) {
        configureAction.execute(getResources());
        return this;
    }

    public SourceDirectorySet getAllSource() {
        return allSource;
    }
}
