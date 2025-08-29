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
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.internal.component.ConfigurationSoftwareComponentVariant;
import org.gradle.language.cpp.internal.NativeDependencyCache;
import org.gradle.language.cpp.internal.NativeVariantIdentity;
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithExecutable;
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithRuntimeUsage;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.swift.SwiftExecutable;
import org.gradle.language.swift.SwiftPlatform;
import org.gradle.nativeplatform.Linkage;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Set;

public abstract class DefaultSwiftExecutable extends DefaultSwiftBinary implements SwiftExecutable, ConfigurableComponentWithExecutable, ConfigurableComponentWithRuntimeUsage, SoftwareComponentInternal {
    @Inject
    public DefaultSwiftExecutable(Names names, ObjectFactory objectFactory, NativeDependencyCache nativeDependencyCache, TaskDependencyFactory taskDependencyFactory, Provider<String> module, boolean testable, FileCollection source, ConfigurationContainer configurations, Configuration implementation, SwiftPlatform targetPlatform, NativeToolChainInternal toolChain, PlatformToolProvider platformToolProvider, NativeVariantIdentity identity) {
        super(names, objectFactory, nativeDependencyCache, taskDependencyFactory, module, testable, source, configurations, implementation, targetPlatform, toolChain, platformToolProvider, identity);
    }

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
}
