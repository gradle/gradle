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
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.UnionFileTree;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.specs.Spec;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GUtil;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.util.concurrent.Callable;

public class DefaultSourceSet implements SourceSet {
    private final String name;
    private final FileResolver fileResolver;
    private File classesDir;
    private FileCollection compileClasspath;
    private FileCollection runtimeClasspath;
    private final SourceDirectorySet javaSource;
    private final UnionFileTree allJavaSource;
    private final SourceDirectorySet resources;
    private final DefaultConfigurableFileCollection classes;
    private final String displayName;
    private final UnionFileTree allSource;

    public DefaultSourceSet(String name, FileResolver fileResolver, TaskResolver taskResolver) {
        this.name = name;
        this.fileResolver = fileResolver;
        displayName = GUtil.toWords(this.name);

        String javaSrcDisplayName = String.format("%s Java source", displayName);
        javaSource = new DefaultSourceDirectorySet(javaSrcDisplayName, fileResolver);
        javaSource.getFilter().include("**/*.java");

        allJavaSource = new UnionFileTree(javaSrcDisplayName, javaSource.matching(javaSource.getFilter()));

        String resourcesDisplayName = String.format("%s resources", displayName);
        resources = new DefaultSourceDirectorySet(resourcesDisplayName, fileResolver);
        resources.getFilter().exclude(new Spec<FileTreeElement>() {
            public boolean isSatisfiedBy(FileTreeElement element) {
                return javaSource.contains(element.getFile());
            }
        });

        String allSourceDisplayName = String.format("%s source", displayName);
        allSource = new UnionFileTree(allSourceDisplayName, resources, javaSource);

        String classesDisplayName = String.format("%s classes", displayName);
        classes = new DefaultConfigurableFileCollection(classesDisplayName, fileResolver, taskResolver, new Callable() {
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

    public String getTaskName(String verb, String target) {
        if (verb == null) {
            return StringUtils.uncapitalize(String.format("%s%s", getTaskBaseName(), StringUtils.capitalize(target)));
        }
        if (target == null) {
            return StringUtils.uncapitalize(String.format("%s%s", verb, GUtil.toCamelCase(name)));
        }
        return StringUtils.uncapitalize(String.format("%s%s%s", verb, getTaskBaseName(), StringUtils.capitalize(target)));
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

    public SourceDirectorySet getResources() {
        return resources;
    }

    public SourceSet resources(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getResources());
        return this;
    }

    public FileTree getAllSource() {
        return allSource;
    }
}
