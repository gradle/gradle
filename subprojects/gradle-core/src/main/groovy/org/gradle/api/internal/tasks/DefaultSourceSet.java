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
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.DefaultFileCollection;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.UnionFileTree;

import java.io.File;

public class DefaultSourceSet implements SourceSet {
    private final String name;
    private File classesDir;
    private FileCollection compileClasspath;
    private FileCollection runtimeClasspath;
    private SourceDirectorySet javaSource;
    private UnionFileTree allJavaSource;
    private SourceDirectorySet resources;

    public DefaultSourceSet(String name, String displayName, FileResolver resolver) {
        this.name = name;
        compileClasspath = new DefaultFileCollection();
        runtimeClasspath = new DefaultFileCollection();
        javaSource = new DefaultSourceDirectorySet(String.format("%s java source", displayName), resolver);
        allJavaSource = new UnionFileTree(String.format("%s java source", displayName), javaSource);
        resources = new DefaultSourceDirectorySet(String.format("%s resources", displayName), resolver);
    }

    public String getName() {
        return name;
    }

    public File getClassesDir() {
        return classesDir;
    }

    public void setClassesDir(File classesDir) {
        this.classesDir = classesDir;
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

    public FileTree getAllJava() {
        return allJavaSource;
    }

    public SourceDirectorySet getResources() {
        return resources;
    }
}
