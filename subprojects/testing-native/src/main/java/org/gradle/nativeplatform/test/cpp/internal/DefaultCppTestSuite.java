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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.cpp.internal.DefaultCppComponent;
import org.gradle.nativeplatform.test.cpp.CppTestExecutable;
import org.gradle.nativeplatform.test.cpp.CppTestSuite;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;

public class DefaultCppTestSuite extends DefaultCppComponent implements CppTestSuite {
    private final ObjectFactory objectFactory;
    private final Property<CppComponent> testedComponent;
    private final Property<CppTestExecutable> testBinary;

    @Inject
    public DefaultCppTestSuite(String name, ObjectFactory objectFactory, final FileOperations fileOperations, ConfigurationContainer configurations) {
        super(name, fileOperations, objectFactory, configurations);
        this.objectFactory = objectFactory;
        this.testedComponent = objectFactory.property(CppComponent.class);
        this.testBinary = objectFactory.property(CppTestExecutable.class);
    }

    public CppTestExecutable addExecutable(String nameSuffix, CppPlatform targetPlatform, NativeToolChainInternal toolChain, PlatformToolProvider platformToolProvider) {
        CppTestExecutable testBinary = objectFactory.newInstance(DefaultCppTestExecutable.class, getName() + StringUtils.capitalize(nameSuffix), getBaseName(), true, false, getCppSource(), getPrivateHeaderDirs(), getImplementationDependencies(), getTestedComponent(), targetPlatform, toolChain, platformToolProvider);
        this.testBinary.set(testBinary);
        getBinaries().add(testBinary);
        return testBinary;
    }

    public Property<CppComponent> getTestedComponent() {
        return testedComponent;
    }

    @Override
    public Property<CppTestExecutable> getTestBinary() {
        return testBinary;
    }
}
