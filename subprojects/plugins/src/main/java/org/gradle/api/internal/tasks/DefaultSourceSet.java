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
import org.gradle.api.internal.jvm.ClassDirectoryBinaryNamingScheme;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;

import static org.gradle.util.ConfigureUtil.configure;

public abstract class DefaultSourceSet implements SourceSet {
    private final String name;
    private final String baseName;
    private FileCollection compileClasspath;
    private FileCollection annotationProcessorPath;
    private FileCollection runtimeClasspath;
    private final SourceDirectorySet javaSource;
    private final SourceDirectorySet allJavaSource;
    private final SourceDirectorySet resources;
    private final String displayName;
    private final SourceDirectorySet allSource;
    private final ClassDirectoryBinaryNamingScheme namingScheme;
    private DefaultSourceSetOutput output;

    public DefaultSourceSet(String name, ObjectFactory objectFactory) {
        this.name = name;
        this.baseName = name.equals(SourceSet.MAIN_SOURCE_SET_NAME) ? "" : GUtil.toCamelCase(name);
        displayName = GUtil.toWords(this.name);
        namingScheme = new ClassDirectoryBinaryNamingScheme(name);

        String javaSrcDisplayName = displayName + " Java source";

        javaSource = objectFactory.sourceDirectorySet("java", javaSrcDisplayName);
        javaSource.getFilter().include("**/*.java");

        allJavaSource = objectFactory.sourceDirectorySet("alljava", javaSrcDisplayName);
        allJavaSource.getFilter().include("**/*.java");
        allJavaSource.source(javaSource);

        String resourcesDisplayName = displayName + " resources";
        resources = objectFactory.sourceDirectorySet("resources", resourcesDisplayName);
        resources.getFilter().exclude(new Spec<FileTreeElement>() {
            @Override
            public boolean isSatisfiedBy(FileTreeElement element) {
                return javaSource.contains(element.getFile());
            }
        });

        String allSourceDisplayName = displayName + " source";
        allSource = objectFactory.sourceDirectorySet("allsource", allSourceDisplayName);
        allSource.source(resources);
        allSource.source(javaSource);

    }

    @Override
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

    @Override
    public String getClassesTaskName() {
        return getTaskName(null, "classes");
    }

    @Override
    public String getCompileTaskName(String language) {
        return getTaskName("compile", language);
    }

    @Override
    public String getCompileJavaTaskName() {
        return getCompileTaskName("java");
    }

    @Override
    public String getProcessResourcesTaskName() {
        return getTaskName("process", "resources");
    }

    @Override
    public String getJarTaskName() {
        return getTaskName(null, "jar");
    }

    @Override
    public String getTaskName(@Nullable String verb, @Nullable String target) {
        return namingScheme.getTaskName(verb, target);
    }

    private String getTaskBaseName() {
        return baseName;
    }

    @Override
    public String getCompileConfigurationName() {
        return configurationNameOf(JavaPlugin.COMPILE_CONFIGURATION_NAME);
    }

    private String configurationNameOf(String baseName) {
        return StringUtils.uncapitalize(getTaskBaseName() + StringUtils.capitalize(baseName));
    }

    @Override
    public String getRuntimeConfigurationName() {
        return configurationNameOf(JavaPlugin.RUNTIME_CONFIGURATION_NAME);
    }

    @Override
    public String getCompileOnlyConfigurationName() {
        return configurationNameOf(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME);
    }

    @Override
    public String getCompileClasspathConfigurationName() {
        return configurationNameOf(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME);
    }

    @Override
    public String getAnnotationProcessorConfigurationName() {
        return configurationNameOf(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME);
    }

    @Override
    public String getApiConfigurationName() {
        return configurationNameOf(JavaPlugin.API_CONFIGURATION_NAME);
    }

    @Override
    public String getImplementationConfigurationName() {
        return configurationNameOf(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME);
    }

    @Override
    public String getApiElementsConfigurationName() {
        return configurationNameOf(JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME);
    }

    @Override
    public String getRuntimeOnlyConfigurationName() {
        return configurationNameOf(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME);
    }

    @Override
    public String getRuntimeClasspathConfigurationName() {
        return configurationNameOf(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
    }

    @Override
    public String getRuntimeElementsConfigurationName() {
        return configurationNameOf(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME);
    }

    @Override
    public SourceSetOutput getOutput() {
        return output;
    }

    public void setClasses(DefaultSourceSetOutput classes) {
        this.output = classes;
    }

    @Override
    public SourceSet compiledBy(Object... taskPaths) {
        output.builtBy(taskPaths);
        return this;
    }

    @Override
    public FileCollection getCompileClasspath() {
        return compileClasspath;
    }

    @Override
    public FileCollection getAnnotationProcessorPath() {
        return annotationProcessorPath;
    }

    @Override
    public FileCollection getRuntimeClasspath() {
        return runtimeClasspath;
    }

    @Override
    public void setCompileClasspath(FileCollection classpath) {
        compileClasspath = classpath;
    }

    @Override
    public void setAnnotationProcessorPath(FileCollection annotationProcessorPath) {
        this.annotationProcessorPath = annotationProcessorPath;
    }

    @Override
    public void setRuntimeClasspath(FileCollection classpath) {
        runtimeClasspath = classpath;
    }

    @Override
    public SourceDirectorySet getJava() {
        return javaSource;
    }

    @Override
    public SourceSet java(@Nullable Closure configureClosure) {
        configure(configureClosure, getJava());
        return this;
    }

    @Override
    public SourceSet java(Action<? super SourceDirectorySet> configureAction) {
        configureAction.execute(getJava());
        return this;
    }

    @Override
    public SourceDirectorySet getAllJava() {
        return allJavaSource;
    }

    @Override
    public SourceDirectorySet getResources() {
        return resources;
    }

    @Override
    public SourceSet resources(@Nullable Closure configureClosure) {
        configure(configureClosure, getResources());
        return this;
    }

    @Override
    public SourceSet resources(Action<? super SourceDirectorySet> configureAction) {
        configureAction.execute(getResources());
        return this;
    }

    @Override
    public SourceDirectorySet getAllSource() {
        return allSource;
    }
}
