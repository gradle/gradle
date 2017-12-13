/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.swift.internal;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.language.swift.SwiftStaticLibrary;
import org.gradle.nativeplatform.tasks.CreateStaticLibrary;

import javax.inject.Inject;

public class DefaultSwiftStaticLibrary extends DefaultSwiftBinary implements SwiftStaticLibrary {
    private final RegularFileProperty linkFile;
    private final Property<CreateStaticLibrary> createTaskProperty;

    @Inject
    public DefaultSwiftStaticLibrary(String name, ProjectLayout projectLayout, ObjectFactory objectFactory, Provider<String> module, boolean debuggable, boolean optimized, boolean testable, FileCollection source, ConfigurationContainer configurations, Configuration implementation) {
        super(name, projectLayout, objectFactory, module, debuggable, optimized, testable, source, configurations, implementation);
        this.linkFile = projectLayout.fileProperty();
        this.createTaskProperty = objectFactory.property(CreateStaticLibrary.class);
    }

    @Override
    public RegularFileProperty getLinkFile() {
        return linkFile;
    }

    @Override
    public Property<CreateStaticLibrary> getCreateTask() {
        return createTaskProperty;
    }
}
