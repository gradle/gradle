/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.*;
import org.gradle.util.GUtil;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.util.concurrent.Callable;

import groovy.lang.Closure;

public class DefaultSourceSet implements SourceSet {
    private final String name;
    private final FileResolver fileResolver;
    private File classesDir;
    private FileCollection compileClasspath;
    private FileCollection runtimeClasspath;
    private final SourceDirectorySet javaSource;
    private final UnionFileTree allJavaSource;
    private final SourceDirectorySet resources;
    private final PatternFilterable javaSourcePatterns = new PatternSet();
    private final PathResolvingFileCollection classes;
    private final String displayName;

    public DefaultSourceSet(String name, FileResolver fileResolver, TaskResolver taskResolver) {
        this.name = name;
        this.fileResolver = fileResolver;
        displayName = GUtil.toWords(this.name);
        String javaSrcDisplayName = String.format("%s Java source", displayName);
        javaSource = new DefaultSourceDirectorySet(javaSrcDisplayName, fileResolver);
        javaSourcePatterns.include("**/*.java");
        allJavaSource = new UnionFileTree(javaSrcDisplayName, javaSource.matching(javaSourcePatterns));
        String resourcesDisplayName = String.format("%s resources", displayName);
        resources = new DefaultSourceDirectorySet(resourcesDisplayName, fileResolver);
        String classesDisplayName = String.format("%s classes", displayName);
        classes = new PathResolvingFileCollection(classesDisplayName, fileResolver, taskResolver, new Callable() {
            public Object call() throws Exception {
                return getClassesDir();
            }
        });
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return String.format("source set %s", getDisplayName());
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCompileTaskName() {
        return String.format("compile%s", getTaskBaseName());
    }

    public String getCompileJavaTaskName() {
        return String.format("compile%sJava", getTaskBaseName());
    }

    public String getProcessResourcesTaskName() {
        return String.format("process%sResources", getTaskBaseName());
    }

    private String getTaskBaseName() {
        return name.equals(SourceSet.MAIN_SOURCE_SET_NAME) ? "" : GUtil.toCamelCase(name);
    }

    public File getClassesDir() {
        return classesDir;
    }

    public void setClassesDir(File classesDir) {
        this.classesDir = fileResolver.resolve(classesDir);
    }

    public FileCollection getClasses() {
        return classes;
    }

    public SourceSet compiledBy(Object... taskPaths) {
        classes.builtBy(taskPaths);
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
        ConfigureUtil.configure(configureClosure, getJava());
        return this;
    }

    public FileTree getAllJava() {
        return allJavaSource;
    }

    public PatternFilterable getJavaSourcePatterns() {
        return javaSourcePatterns;
    }

    public SourceDirectorySet getResources() {
        return resources;
    }

    public SourceSet resources(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getResources());
        return this;
    }
}
