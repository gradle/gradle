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
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.language.nativeplatform.internal.DefaultNativeComponent;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.swift.SwiftComponent;

import java.util.Collections;

public abstract class DefaultSwiftComponent extends DefaultNativeComponent implements SwiftComponent {
    private final FileCollection swiftSource;
    private final Property<String> module;
    private final String name;
    private final Names names;
    private final Configuration implementation;

    public DefaultSwiftComponent(String name, FileOperations fileOperations, ObjectFactory objectFactory, ConfigurationContainer configurations) {
        super(fileOperations);
        this.name = name;
        swiftSource = createSourceView("src/"+ name + "/swift", Collections.singletonList("swift"));
        module = objectFactory.property(String.class);

        names = Names.of(name);
        implementation = configurations.maybeCreate(names.withSuffix("implementation"));
        implementation.setCanBeConsumed(false);
        implementation.setCanBeResolved(false);
    }

    @Override
    public String getName() {
        return name;
    }

    protected Names getNames() {
        return names;
    }

    @Override
    public Property<String> getModule() {
        return module;
    }

    @Override
    public FileCollection getSwiftSource() {
        return swiftSource;
    }

    @Override
    public Configuration getImplementationDependencies() {
        return implementation;
    }
}
