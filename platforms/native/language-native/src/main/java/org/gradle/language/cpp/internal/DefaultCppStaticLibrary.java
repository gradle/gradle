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

import com.google.common.collect.Sets;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.internal.component.ConfigurationSoftwareComponentVariant;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.cpp.CppStaticLibrary;
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithLinkUsage;
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithRuntimeUsage;
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithStaticLibrary;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.nativeplatform.Linkage;
import org.gradle.nativeplatform.tasks.CreateStaticLibrary;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.Set;

public class DefaultCppStaticLibrary extends DefaultCppBinary implements CppStaticLibrary, ConfigurableComponentWithStaticLibrary, ConfigurableComponentWithLinkUsage, ConfigurableComponentWithRuntimeUsage, SoftwareComponentInternal {
    private final RegularFileProperty linkFile;
    private final Property<Task> linkFileProducer;
    private final Property<CreateStaticLibrary> createTaskProperty;
    private final Property<Configuration> linkElements;
    private final Property<Configuration> runtimeElements;
    private final ConfigurableFileCollection outputs;

    @Inject
    public DefaultCppStaticLibrary(Names names, ObjectFactory objectFactory, Provider<String> baseName, FileCollection sourceFiles, FileCollection componentHeaderDirs, RoleBasedConfigurationContainerInternal configurations, Configuration implementation, CppPlatform targetPlatform, NativeToolChainInternal toolChain, PlatformToolProvider platformToolProvider, NativeVariantIdentity identity) {
        super(names, objectFactory, baseName, sourceFiles, componentHeaderDirs, configurations, implementation, targetPlatform, toolChain, platformToolProvider, identity);
        this.linkFile = objectFactory.fileProperty();
        this.linkFileProducer = objectFactory.property(Task.class);
        this.createTaskProperty = objectFactory.property(CreateStaticLibrary.class);
        this.linkElements = objectFactory.property(Configuration.class);
        this.runtimeElements = objectFactory.property(Configuration.class);
        this.outputs = objectFactory.fileCollection();
    }

    @Override
    public ConfigurableFileCollection getOutputs() {
        return outputs;
    }

    @Override
    public RegularFileProperty getLinkFile() {
        return linkFile;
    }

    @Override
    public Property<Task> getLinkFileProducer() {
        return linkFileProducer;
    }

    @Override
    public Property<CreateStaticLibrary> getCreateTask() {
        return createTaskProperty;
    }

    @Override
    public Property<Configuration> getLinkElements() {
        return linkElements;
    }

    @Override
    public Property<Configuration> getRuntimeElements() {
        return runtimeElements;
    }

    @Nullable
    @Override
    public Linkage getLinkage() {
        return Linkage.STATIC;
    }

    @Override
    public boolean hasRuntimeFile() {
        return false;
    }

    @Override
    public Provider<RegularFile> getRuntimeFile() {
        return Providers.notDefined();
    }

    @Override
    public Set<? extends UsageContext> getUsages() {
        Configuration linkElements = getLinkElements().get();
        Configuration runtimeElements = getRuntimeElements().get();
        // TODO: Does a static library really have any runtime elements?
        return Sets.newHashSet(
            new ConfigurationSoftwareComponentVariant(getIdentity().getLinkVariant(), linkElements.getAllArtifacts(), linkElements),
            new ConfigurationSoftwareComponentVariant(getIdentity().getRuntimeVariant(), runtimeElements.getAllArtifacts(), runtimeElements)
        );
    }

    @Override
    public AttributeContainer getLinkAttributes() {
        return getIdentity().getLinkVariant().getAttributes();
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
