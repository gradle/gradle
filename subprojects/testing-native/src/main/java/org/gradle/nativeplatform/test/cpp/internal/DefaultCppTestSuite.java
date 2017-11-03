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

package org.gradle.nativeplatform.test.cpp.internal;

import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.internal.DefaultCppComponent;
import org.gradle.nativeplatform.test.cpp.CppTestSuite;

import javax.inject.Inject;

public class DefaultCppTestSuite extends DefaultCppComponent implements CppTestSuite {
    private final Property<CppComponent> testedComponent;
    private final CppExecutable testBinary;

    @Inject
    public DefaultCppTestSuite(String name, ProjectLayout projectLayout, ObjectFactory objectFactory, final FileOperations fileOperations, ConfigurationContainer configurations) {
        super(name, fileOperations, objectFactory, configurations);
        this.testedComponent = objectFactory.property(CppComponent.class);
        this.testBinary = objectFactory.newInstance(DefaultCppTestExecutable.class, name + "Executable", projectLayout, objectFactory, getBaseName(), true, getCppSource(), getPrivateHeaderDirs(), configurations, getImplementationDependencies(), getTestedComponent());
        getBaseName().set(name);
    }

    public Property<CppComponent> getTestedComponent() {
        return testedComponent;
    }

    @Override
    public CppExecutable getTestExecutable() {
        return testBinary;
    }

    @Override
    public CppExecutable getDevelopmentBinary() {
        return testBinary;
    }
}
