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

package org.gradle.nativeplatform.test.xctest.internal;

import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.language.swift.SwiftComponent;
import org.gradle.language.swift.internal.DefaultSwiftComponent;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestBinary;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestSuite;

import javax.inject.Inject;

public class DefaultSwiftXCTestSuite extends DefaultSwiftComponent implements SwiftXCTestSuite {
    private Property<SwiftComponent> testedComponent;
    private final SwiftXCTestBinary testBinary;

    @Inject
    public DefaultSwiftXCTestSuite(String name, ProjectLayout projectLayout, FileOperations fileOperations, ObjectFactory objectFactory, ConfigurationContainer configurations) {
        super(name, fileOperations, objectFactory, configurations);
        this.testBinary = objectFactory.newInstance(DefaultSwiftXCTestBinary.class, name + "Executable", projectLayout, objectFactory, getModule(), true, true, getSwiftSource(), configurations, getImplementationDependencies());
        this.testedComponent = objectFactory.property(SwiftComponent.class);
    }

    public Property<SwiftComponent> getTestedComponent() {
        return testedComponent;
    }

    @Override
    public SwiftXCTestBinary getDevelopmentBinary() {
        return testBinary;
    }

    @Override
    public SwiftXCTestBinary getTestExecutable() {
        return testBinary;
    }
}
