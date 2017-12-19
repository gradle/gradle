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

package org.gradle.language.cpp.internal;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;

public class DefaultCppApplication extends DefaultCppComponent implements CppApplication {
    private final ProjectLayout projectLayout;
    private final ObjectFactory objectFactory;
    private final ConfigurationContainer configurations;
    private DefaultCppExecutable debug;
    private DefaultCppExecutable release;

    @Inject
    public DefaultCppApplication(String name, ProjectLayout projectLayout, ObjectFactory objectFactory, FileOperations fileOperations, ConfigurationContainer configurations) {
        super(name, fileOperations, objectFactory, configurations);
        this.projectLayout = projectLayout;
        this.objectFactory = objectFactory;
        this.configurations = configurations;
    }

    public DefaultCppExecutable createExecutable(String nameSuffix, boolean debuggable, boolean optimized, NativePlatform targetPlatform, NativeToolChainInternal toolChain, PlatformToolProvider platformToolProvider) {
        DefaultCppExecutable result = objectFactory.newInstance(DefaultCppExecutable.class, getName() + StringUtils.capitalize(nameSuffix), projectLayout, objectFactory, getBaseName(), debuggable, optimized, getCppSource(), getPrivateHeaderDirs(), configurations, getImplementationDependencies(), targetPlatform, toolChain, platformToolProvider);
        if (debuggable && !optimized) {
            debug = result;
        } else if (debuggable && optimized){
            release = result;
        }
        return result;
    }

    @Override
    public CppExecutable getDevelopmentBinary() {
        return debug;
    }

    @Override
    public CppExecutable getDebugExecutable() {
        return debug;
    }

    @Override
    public CppExecutable getReleaseExecutable() {
        return release;
    }
}
