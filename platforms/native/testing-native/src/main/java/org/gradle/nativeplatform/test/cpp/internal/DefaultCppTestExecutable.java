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

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.cpp.internal.DefaultCppBinary;
import org.gradle.language.cpp.internal.DefaultCppComponent;
import org.gradle.language.cpp.internal.NativeVariantIdentity;
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithExecutable;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.nativeplatform.test.cpp.CppTestExecutable;
import org.gradle.nativeplatform.test.tasks.RunTestExecutable;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;
import java.util.concurrent.Callable;

public abstract class DefaultCppTestExecutable extends DefaultCppBinary implements CppTestExecutable, ConfigurableComponentWithExecutable {
    private final ProjectLayout projectLayout;
    private final Provider<CppComponent> testedComponent;

    @Inject
    public DefaultCppTestExecutable(Names names, ProjectLayout projectLayout, Provider<String> baseName, FileCollection sourceFiles, FileCollection componentHeaderDirs, Configuration implementation, Provider<CppComponent> testedComponent, CppPlatform targetPlatform, NativeToolChainInternal toolChain, PlatformToolProvider platformToolProvider, NativeVariantIdentity identity, RoleBasedConfigurationContainerInternal configurations, ObjectFactory objects) {
        super(names, objects, baseName, sourceFiles, componentHeaderDirs, configurations, implementation, targetPlatform, toolChain, platformToolProvider, identity);
        this.projectLayout = projectLayout;
        this.testedComponent = testedComponent;
    }

    @Override
    public abstract Property<RunTestExecutable> getRunTask();

    @Override
    public FileCollection getCompileIncludePath() {
        // TODO: This should be modeled differently, perhaps as a dependency on the implementation configuration
        return super.getCompileIncludePath().plus(projectLayout.files(new Callable<FileCollection>() {
            @Override
            public FileCollection call() {
                CppComponent tested = testedComponent.getOrNull();
                if (tested == null) {
                    return projectLayout.files();
                }
                return ((DefaultCppComponent) tested).getAllHeaderDirs();
            }
        }));
    }
}
