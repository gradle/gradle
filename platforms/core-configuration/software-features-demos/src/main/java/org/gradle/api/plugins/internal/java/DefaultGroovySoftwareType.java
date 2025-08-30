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
import org.gradle.api.plugins.java.GroovySoftwareType;
import org.jspecify.annotations.NullMarked;

import javax.inject.Inject;

@NullMarked
abstract public class DefaultGroovySoftwareType implements GroovySoftwareType {
    private final NamedDomainObjectContainer<GroovySources> sourcesContainer;

    @Inject
    public DefaultGroovySoftwareType(ObjectFactory objectFactory) {
        this.sourcesContainer = objectFactory.domainObjectContainer(GroovySources.class, name -> objectFactory.newInstance(DefaultGroovySources.class, name));
    }

    @Override
    public NamedDomainObjectContainer<GroovySources> getSources() {
        return sourcesContainer;
    }

    static class DefaultGroovySources implements GroovySources {
        private final String name;
        private final SourceDirectorySet javaSources;
        private final SourceDirectorySet resources;

        @Inject
        public DefaultGroovySources(String name, ObjectFactory objectFactory) {
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
