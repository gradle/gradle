/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.plugins.internal.java;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.java.HasJavaSources;
import org.gradle.api.plugins.java.JavaSoftwareType;
import org.jspecify.annotations.NullMarked;

import javax.inject.Inject;

@NullMarked
abstract public class DefaultJavaSoftwareType implements JavaSoftwareType {
    private final NamedDomainObjectContainer<JavaSources> sourcesContainer;

    @Inject
    public DefaultJavaSoftwareType(ObjectFactory objectFactory) {
        this.sourcesContainer = objectFactory.domainObjectContainer(HasJavaSources.JavaSources.class, name -> objectFactory.newInstance(DefaultJavaSources.class, name));
    }

    @Override
    public NamedDomainObjectContainer<JavaSources> getSources() {
        return sourcesContainer;
    }

    static class DefaultJavaSources implements JavaSources {
        private final String name;
        private final SourceDirectorySet javaSources;
        private final SourceDirectorySet resources;

        @Inject
        public DefaultJavaSources(String name, ObjectFactory objectFactory) {
            this.name = name;
            this.javaSources = objectFactory.sourceDirectorySet(name, name + " java sources"); // Initialize with actual SourceDirectorySet
            this.resources = objectFactory.sourceDirectorySet(name, name + " resources"); // Initialize with actual SourceDirectorySet
        }

        @Override
        public SourceDirectorySet getSourceDirectories() {
            return javaSources;
        }

        @Override
        public SourceDirectorySet getResources() {
            return resources;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
