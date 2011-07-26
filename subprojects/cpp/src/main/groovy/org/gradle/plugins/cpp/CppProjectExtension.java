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

import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.internal.FactoryNamedDomainObjectContainer;
import org.gradle.api.internal.ReflectiveNamedDomainObjectFactory;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.ConfigureUtil;

import org.gradle.plugins.cpp.source.CppSourceSet;
import org.gradle.plugins.cpp.source.internal.DefaultCppSourceSet;
import org.gradle.plugins.cpp.built.CppExecutable;
import org.gradle.plugins.cpp.built.internal.DefaultCppExecutable;
import org.gradle.plugins.cpp.built.CppLibrary;
import org.gradle.plugins.cpp.built.internal.DefaultCppLibrary;


import org.gradle.plugins.cpp.dsl.LibraryDsl;
import org.gradle.plugins.cpp.dsl.ExecutableDsl;

import groovy.lang.Closure;

/**
 * The DSL for C++.
 */
public class CppProjectExtension {

    static public final String DEFAULT_NAME = "main";

    final private ProjectInternal project;

    final private NamedDomainObjectContainer<CppSourceSet> sourceSets;
    final private NamedDomainObjectContainer<CppLibrary> libraries;
    final private NamedDomainObjectContainer<CppExecutable> executables;

    public CppProjectExtension(Project project) {
        this.project = (ProjectInternal)project;

        ClassGenerator classGenerator = this.project.getServices().get(ClassGenerator.class);
        
        this.sourceSets = classGenerator.newInstance(
                FactoryNamedDomainObjectContainer.class,
                CppSourceSet.class,
                classGenerator,
                new ReflectiveNamedDomainObjectFactory<CppSourceSet>(
                        DefaultCppSourceSet.class, 
                        classGenerator, 
                        this.project.getFileResolver()
                )
        );
        
        this.libraries = classGenerator.newInstance(FactoryNamedDomainObjectContainer.class, DefaultCppLibrary.class, classGenerator);
        this.executables = classGenerator.newInstance(FactoryNamedDomainObjectContainer.class, DefaultCppExecutable.class, classGenerator);
    }

    public Project getProject() {
        return project;
    }

    public NamedDomainObjectContainer<CppSourceSet> getSourceSets() {
        return sourceSets;
    }

    public NamedDomainObjectContainer<CppSourceSet> sourceSets(Closure closure) {
        return sourceSets.configure(closure);
    }

    public CppExecutable executable(Closure closure) {
        return executable(DEFAULT_NAME, closure);
    }

    public CppExecutable executable(String name, Closure closure) {
        ExecutableDsl dsl = new ExecutableDsl(this, name);
        ConfigureUtil.configure(closure, dsl);
        CppExecutable executable = dsl.create();
        executables.add(executable);
        return executable;
    }

    public NamedDomainObjectContainer<CppExecutable> getExecutables() {
        return executables;
    }

    public CppLibrary library(Closure closure) {
        return library(DEFAULT_NAME, closure);
    }

    public CppLibrary library(String name, Closure closure) {
        LibraryDsl dsl = new LibraryDsl(this, name);
        ConfigureUtil.configure(closure, dsl);
        CppLibrary library = dsl.create();
        libraries.add(library);
        return library;
    }

    public NamedDomainObjectContainer<CppLibrary> getLibraries() {
        return libraries;
    }
}
