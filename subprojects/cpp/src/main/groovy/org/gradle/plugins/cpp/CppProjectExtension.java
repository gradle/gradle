/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.cpp;

import org.gradle.api.Project;

import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.ConfigureUtil;

import org.gradle.plugins.cpp.source.CppSourceSetContainer;
import org.gradle.plugins.cpp.source.internal.DefaultCppSourceSetContainer;
import org.gradle.plugins.cpp.built.CppExecutable;
import org.gradle.plugins.cpp.built.CppExecutableContainer;
import org.gradle.plugins.cpp.built.internal.DefaultCppExecutableContainer;
import org.gradle.plugins.cpp.built.CppLibrary;
import org.gradle.plugins.cpp.built.CppLibraryContainer;
import org.gradle.plugins.cpp.built.internal.DefaultCppLibraryContainer;

import org.gradle.plugins.cpp.dsl.LibraryDsl;
import org.gradle.plugins.cpp.dsl.ExecutableDsl;

import groovy.lang.Closure;

/**
 * The DSL for C++.
 */
public class CppProjectExtension {

    static public final String DEFAULT_NAME = "main";

    final private ProjectInternal project;

    final private CppSourceSetContainer sourceSets;
    final private CppLibraryContainer libraries;
    final private CppExecutableContainer executables;

    public CppProjectExtension(Project project) {
        this.project = (ProjectInternal)project;

        ClassGenerator classGenerator = this.project.getServices().get(ClassGenerator.class);
        this.sourceSets = classGenerator.newInstance(DefaultCppSourceSetContainer.class, this.project.getFileResolver(), classGenerator);
        this.libraries = classGenerator.newInstance(DefaultCppLibraryContainer.class, classGenerator);
        this.executables = classGenerator.newInstance(DefaultCppExecutableContainer.class, classGenerator);
    }

    public Project getProject() {
        return project;
    }

    public CppSourceSetContainer getSourceSets() {
        return sourceSets;
    }

    public CppSourceSetContainer sourceSets(Closure closure) {
        return sourceSets.configure(closure);
    }

    public CppExecutable executable(Closure closure) {
        return executable(DEFAULT_NAME, closure);
    }

    public CppExecutable executable(String name, Closure closure) {
        ExecutableDsl dsl = new ExecutableDsl(this, name);
        ConfigureUtil.configure(closure, dsl);
        return executables.add(dsl.create());
    }

    public CppExecutableContainer getExecutables() {
        return executables;
    }

    public CppLibrary library(Closure closure) {
        return library(DEFAULT_NAME, closure);
    }

    public CppLibrary library(String name, Closure closure) {
        LibraryDsl dsl = new LibraryDsl(this, name);
        ConfigureUtil.configure(closure, dsl);
        return libraries.add(dsl.create());
    }

    public CppLibraryContainer getLibraries() {
        return libraries;
    }
}
