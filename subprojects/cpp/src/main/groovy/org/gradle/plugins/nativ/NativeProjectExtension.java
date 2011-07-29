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
package org.gradle.plugins.nativ;

import org.gradle.api.Project;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.internal.FactoryNamedDomainObjectContainer;
import org.gradle.api.internal.ReflectiveNamedDomainObjectFactory;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.project.ProjectInternal;

import org.gradle.plugins.nativ.model.NativeSourceSet;
import org.gradle.plugins.nativ.model.Binary;
import org.gradle.plugins.nativ.model.internal.DefaultBinary;
import org.gradle.plugins.nativ.model.internal.DefaultNativeSourceSet;

import org.gradle.plugins.cpp.gcc.GppCompileSpec;

import groovy.lang.Closure;

public class NativeProjectExtension {
    
    final private ProjectInternal project;

    final private NamedDomainObjectContainer<NativeSourceSet> sourceSets;
    final private NamedDomainObjectContainer<Binary> binaries;

    public NativeProjectExtension(final Project project) {
        this.project = (ProjectInternal)project;

        ClassGenerator classGenerator = this.project.getServices().get(ClassGenerator.class);

        this.sourceSets = classGenerator.newInstance(
                FactoryNamedDomainObjectContainer.class,
                NativeSourceSet.class,
                classGenerator,
                new ReflectiveNamedDomainObjectFactory<NativeSourceSet>(
                        DefaultNativeSourceSet.class,
                        classGenerator,
                        classGenerator,
                        this.project.getFileResolver()
                )
        );

        this.binaries = classGenerator.newInstance(
                FactoryNamedDomainObjectContainer.class,
                DefaultBinary.class,
                classGenerator,
                new NamedDomainObjectFactory<Binary>() {
                    public Binary create(String name) {
                        return new DefaultBinary(name, new GppCompileSpec(name, NativeProjectExtension.this.project));
                    }
                }
        );

    }

    public Project getProject() {
        return project;
    }

    public NamedDomainObjectContainer<NativeSourceSet> getSourceSets() {
        return sourceSets;
    }

    public NamedDomainObjectContainer<NativeSourceSet> sourceSets(Closure closure) {
        return sourceSets.configure(closure);
    }

    public NamedDomainObjectContainer<Binary> getBinaries() {
        return binaries;
    }

    public NamedDomainObjectContainer<Binary> binaries(Closure closure) {
        return binaries.configure(closure);
    }
    
}