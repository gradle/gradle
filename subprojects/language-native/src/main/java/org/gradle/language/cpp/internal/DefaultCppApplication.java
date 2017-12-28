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
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.nativeplatform.internal.PublicationAwareComponent;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;

public class DefaultCppApplication extends DefaultCppComponent implements CppApplication, PublicationAwareComponent {
    private final ObjectFactory objectFactory;
    private final Property<CppExecutable> developmentBinary;
    private final MainExecutableVariant mainVariant = new MainExecutableVariant();

    @Inject
    public DefaultCppApplication(String name, ObjectFactory objectFactory, FileOperations fileOperations, ConfigurationContainer configurations) {
        super(name, fileOperations, objectFactory, configurations);
        this.objectFactory = objectFactory;
        this.developmentBinary = objectFactory.property(CppExecutable.class);
    }

    public DefaultCppExecutable addExecutable(String nameSuffix, boolean debuggable, boolean optimized, CppPlatform targetPlatform, NativeToolChainInternal toolChain, PlatformToolProvider platformToolProvider) {
        DefaultCppExecutable result = objectFactory.newInstance(DefaultCppExecutable.class, getName() + StringUtils.capitalize(nameSuffix), getBaseName(), debuggable, optimized, getCppSource(), getPrivateHeaderDirs(), getImplementationDependencies(), targetPlatform, toolChain, platformToolProvider);
        getBinaries().add(result);
        return result;
    }

    @Override
    public MainExecutableVariant getMainPublication() {
        return mainVariant;
    }

    @Override
    public Property<CppExecutable> getDevelopmentBinary() {
        return developmentBinary;
    }
}
