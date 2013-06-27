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
package org.gradle.nativecode.language.cpp.plugins;

import groovy.lang.Closure;
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.internal.FactoryNamedDomainObjectContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.api.internal.ReflectiveNamedDomainObjectFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.nativecode.language.cpp.CppSourceSet;
import org.gradle.nativecode.language.cpp.internal.DefaultCppSourceSet;

/**
 * Configuration for the C++ language.
 */
@Incubating
public class CppExtension {

    final private NamedDomainObjectContainer<CppSourceSet> sourceSets;

    public CppExtension(final ProjectInternal project) {
        Instantiator instantiator = project.getServices().get(Instantiator.class);
        sourceSets = instantiator.newInstance(
                FactoryNamedDomainObjectContainer.class,
                CppSourceSet.class,
                instantiator,
                new ReflectiveNamedDomainObjectFactory<CppSourceSet>(DefaultCppSourceSet.class, project)
        );
    }

    public NamedDomainObjectContainer<CppSourceSet> sourceSets(Closure closure) {
        return sourceSets.configure(closure);
    }

    /**
     * Returns the C++ source sets for this project.
     */
    public NamedDomainObjectContainer<CppSourceSet> getSourceSets() {
        return sourceSets;
    }
}