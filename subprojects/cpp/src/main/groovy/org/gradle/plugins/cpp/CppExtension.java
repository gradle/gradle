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

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.FactoryNamedDomainObjectContainer;
import org.gradle.api.internal.project.ProjectInternal;

import org.gradle.plugins.cpp.internal.DefaultCppSourceSet;

import groovy.lang.Closure;

/**
 * Adds a source set container.
 */
public class CppExtension {

    final private NamedDomainObjectContainer<CppSourceSet> sourceSets;

    public CppExtension(final ProjectInternal project) {
        ClassGenerator classGenerator = project.getServices().get(ClassGenerator.class);
        sourceSets = classGenerator.newInstance(
                FactoryNamedDomainObjectContainer.class,
                DefaultCppSourceSet.class,
                classGenerator,
                new NamedDomainObjectFactory<DefaultCppSourceSet>() {
                    public DefaultCppSourceSet create(String name) {
                        return new DefaultCppSourceSet(name, project.getFileResolver());
                    }
                }
        );
    }

    public NamedDomainObjectContainer<CppSourceSet> sourceSets(Closure closure) {
        return sourceSets.configure(closure);
    }
    
    public NamedDomainObjectContainer<CppSourceSet> getSourceSets() {
        return sourceSets;
    }
}