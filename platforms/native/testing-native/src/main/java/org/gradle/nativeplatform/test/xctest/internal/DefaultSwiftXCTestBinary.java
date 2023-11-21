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

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.language.cpp.internal.NativeVariantIdentity;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.swift.SwiftPlatform;
import org.gradle.language.swift.internal.DefaultSwiftBinary;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestBinary;
import org.gradle.nativeplatform.test.xctest.tasks.XCTest;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

/**
 * Binary of a XCTest suite component.
 * This may be an executable that can be executed directly or a bundle that must be executed through xctest.
 *
 * Either way, the installation provides a single entry point for executing this binary.
 */
public abstract class DefaultSwiftXCTestBinary extends DefaultSwiftBinary implements SwiftXCTestBinary {
    private final RegularFileProperty executableFile;
    private final DirectoryProperty installDirectory;
    private final RegularFileProperty runScriptFile;
    private final Property<XCTest> runTaskProperty;

    public DefaultSwiftXCTestBinary(Names names, ObjectFactory objectFactory, TaskDependencyFactory taskDependencyFactory, Provider<String> module, boolean testable, FileCollection source, ConfigurationContainer configurations, Configuration implementation, SwiftPlatform targetPlatform, NativeToolChainInternal toolChain, PlatformToolProvider platformToolProvider, NativeVariantIdentity identity) {
        super(names, objectFactory, taskDependencyFactory, module, testable, source, configurations, implementation, targetPlatform, toolChain, platformToolProvider, identity);
        this.executableFile = objectFactory.fileProperty();
        this.installDirectory = objectFactory.directoryProperty();
        this.runScriptFile = objectFactory.fileProperty();
        this.runTaskProperty = objectFactory.property(XCTest.class);
    }

    @Override
    public RegularFileProperty getExecutableFile() {
        return executableFile;
    }

    @Override
    public DirectoryProperty getInstallDirectory() {
        return installDirectory;
    }

    @Override
    public RegularFileProperty getRunScriptFile() {
        return runScriptFile;
    }

    @Override
    public Property<XCTest> getRunTask() {
        return runTaskProperty;
    }
}
