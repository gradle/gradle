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

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.internal.component.ConfigurationSoftwareComponentVariant;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithExecutable;
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithRuntimeUsage;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.nativeplatform.Linkage;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Set;

public abstract class DefaultCppExecutable extends DefaultCppBinary implements CppExecutable, ConfigurableComponentWithExecutable, ConfigurableComponentWithRuntimeUsage, SoftwareComponentInternal {
    @Inject
    public DefaultCppExecutable(Names names, ObjectFactory objectFactory, Provider<String> baseName, FileCollection sourceFiles, FileCollection componentHeaderDirs, RoleBasedConfigurationContainerInternal configurations, Configuration implementation, CppPlatform targetPlatform, NativeToolChainInternal toolChain, PlatformToolProvider platformToolProvider, NativeVariantIdentity identity) {
        super(names, objectFactory, baseName, sourceFiles, componentHeaderDirs, configurations, implementation, targetPlatform, toolChain, platformToolProvider, identity);
    }

    @Override
    public abstract RegularFileProperty getRuntimeFile();

    @Nullable
    @Override
    public Linkage getLinkage() {
        return null;
    }

    @Override
    public boolean hasRuntimeFile() {
        return true;
    }

    @Override
    public Set<? extends UsageContext> getUsages() {
        Configuration runtimeElements = getRuntimeElements().get();
        return Collections.singleton(new ConfigurationSoftwareComponentVariant(getIdentity().getRuntimeVariant(), runtimeElements.getAllArtifacts(), runtimeElements));
    }

    @Override
    public AttributeContainer getRuntimeAttributes() {
        return getIdentity().getRuntimeVariant().getAttributes();
    }

    @Override
    public ModuleVersionIdentifier getCoordinates() {
        return getIdentity().getCoordinates();
    }
}
