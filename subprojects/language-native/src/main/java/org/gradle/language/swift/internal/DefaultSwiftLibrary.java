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
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.language.swift.SwiftLibrary;
import org.gradle.language.swift.SwiftSharedLibrary;

import javax.inject.Inject;

public class DefaultSwiftLibrary extends DefaultSwiftComponent implements SwiftLibrary {
    private final DefaultSwiftSharedLibrary debug;
    private final DefaultSwiftSharedLibrary release;
    private final Configuration api;

    @Inject
    public DefaultSwiftLibrary(String name, ProjectLayout projectLayout, ObjectFactory objectFactory, FileOperations fileOperations, ConfigurationContainer configurations) {
        super(name, fileOperations, objectFactory, configurations);
        debug = objectFactory.newInstance(DefaultSwiftSharedLibrary.class, name + "Debug", projectLayout, objectFactory, getModule(), true, true, getSwiftSource(), configurations, getImplementationDependencies());
        release = objectFactory.newInstance(DefaultSwiftSharedLibrary.class, name + "Release", projectLayout, objectFactory, getModule(), false, false, getSwiftSource(), configurations, getImplementationDependencies());

        api = configurations.maybeCreate(getNames().withSuffix("api"));
        api.setCanBeConsumed(false);
        api.setCanBeResolved(false);
        getImplementationDependencies().extendsFrom(api);
    }

    @Override
    public Configuration getApiDependencies() {
        return api;
    }

    @Override
    public SwiftSharedLibrary getDevelopmentBinary() {
        return debug;
    }

    @Override
    public SwiftSharedLibrary getDebugSharedLibrary() {
        return debug;
    }

    @Override
    public SwiftSharedLibrary getReleaseSharedLibrary() {
        return release;
    }
}
