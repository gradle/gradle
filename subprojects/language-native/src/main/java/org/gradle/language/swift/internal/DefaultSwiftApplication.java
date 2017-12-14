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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.language.swift.SwiftApplication;
import org.gradle.language.swift.SwiftExecutable;

import javax.inject.Inject;

public class DefaultSwiftApplication extends DefaultSwiftComponent implements SwiftApplication {
    private final ProjectLayout projectLayout;
    private final ObjectFactory objectFactory;
    private final ConfigurationContainer configurations;
    private final Property<SwiftExecutable> developmentBinary;

    @Inject
    public DefaultSwiftApplication(String name, ProjectLayout projectLayout, ObjectFactory objectFactory, FileOperations fileOperations, ConfigurationContainer configurations) {
        super(name, fileOperations, objectFactory, configurations);
        this.projectLayout = projectLayout;
        this.objectFactory = objectFactory;
        this.configurations = configurations;
        this.developmentBinary = objectFactory.property(SwiftExecutable.class);
    }

    public SwiftExecutable createExecutable(String nameSuffix, boolean debuggable, boolean optimized, boolean testable) {
        SwiftExecutable result = objectFactory.newInstance(DefaultSwiftExecutable.class, getName() + StringUtils.capitalize(nameSuffix), projectLayout, objectFactory, getModule(), debuggable, optimized, testable, getSwiftSource(), configurations, getImplementationDependencies());
        getBinaries().add(result);
        return result;
    }

    @Override
    public Property<SwiftExecutable> getDevelopmentBinary() {
        return developmentBinary;
    }
}
