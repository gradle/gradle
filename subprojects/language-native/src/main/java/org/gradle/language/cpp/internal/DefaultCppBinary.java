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

import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.language.internal.DefaultNativeBinary;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.nativeplatform.MachineArchitecture;
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;

public class DefaultCppBinary extends DefaultNativeBinary implements CppBinary {
    private final Provider<String> baseName;
    private final FileCollection sourceFiles;
    private final FileCollection includePath;
    private final Configuration linkLibraries;
    private final FileCollection runtimeLibraries;
    private final CppPlatform targetPlatform;
    private final NativeToolChainInternal toolChain;
    private final PlatformToolProvider platformToolProvider;
    private final Configuration includePathConfiguration;
    private final Property<CppCompile> compileTaskProperty;
    private final NativeVariantIdentity identity;

    public DefaultCppBinary(Names names, ObjectFactory objects, Provider<String> baseName, FileCollection sourceFiles, FileCollection componentHeaderDirs, RoleBasedConfigurationContainerInternal configurations, Configuration componentImplementation, CppPlatform targetPlatform, NativeToolChainInternal toolChain, PlatformToolProvider platformToolProvider, NativeVariantIdentity identity) {
        super(names, objects, componentImplementation);
        this.baseName = baseName;
        this.sourceFiles = sourceFiles;
        this.targetPlatform = targetPlatform;
        this.toolChain = toolChain;
        this.platformToolProvider = platformToolProvider;
        this.compileTaskProperty = objects.property(CppCompile.class);
        this.identity = identity;

        // TODO - reduce duplication with Swift binary

        @SuppressWarnings("deprecation")
        Configuration ipc = configurations.resolvableDependencyScopeUnlocked(names.withPrefix("cppCompile")).get();
        includePathConfiguration = ipc;
        includePathConfiguration.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.C_PLUS_PLUS_API));
        includePathConfiguration.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, identity.isDebuggable());
        includePathConfiguration.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, identity.isOptimized());
        includePathConfiguration.getAttributes().attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, identity.getTargetMachine().getOperatingSystemFamily());
        includePathConfiguration.getAttributes().attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, identity.getTargetMachine().getArchitecture());
        includePathConfiguration.extendsFrom(getImplementationDependencies());

        @SuppressWarnings("deprecation")
        Configuration nativeLink = configurations.resolvableDependencyScopeUnlocked(names.withPrefix("nativeLink")).get();
        nativeLink.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.NATIVE_LINK));
        nativeLink.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, identity.isDebuggable());
        nativeLink.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, identity.isOptimized());
        nativeLink.getAttributes().attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, identity.getTargetMachine().getOperatingSystemFamily());
        nativeLink.getAttributes().attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, identity.getTargetMachine().getArchitecture());
        nativeLink.extendsFrom(getImplementationDependencies());

        Configuration nativeRuntime = configurations.migratingUnlocked(names.withPrefix("nativeRuntime"), ConfigurationRolesForMigration.RESOLVABLE_BUCKET_TO_RESOLVABLE).get();
        nativeRuntime.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.NATIVE_RUNTIME));
        nativeRuntime.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, identity.isDebuggable());
        nativeRuntime.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, identity.isOptimized());
        nativeRuntime.getAttributes().attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, identity.getTargetMachine().getOperatingSystemFamily());
        nativeRuntime.getAttributes().attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, identity.getTargetMachine().getArchitecture());
        nativeRuntime.extendsFrom(getImplementationDependencies());

        ArtifactView includeDirs = includePathConfiguration.getIncoming().artifactView(viewConfiguration -> {
           viewConfiguration.attributes(attributeContainer -> {
               attributeContainer.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE);
           });
        });
        includePath = componentHeaderDirs.plus(includeDirs.getFiles());
        linkLibraries = nativeLink;
        runtimeLibraries = nativeRuntime;
    }

    @Inject
    protected ProjectLayout getProjectLayout() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected TemporaryFileProvider getTemporaryFileProvider() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected NativeDependencyCache getNativeDependencyCache() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Provider<String> getBaseName() {
        return baseName;
    }

    @Override
    public boolean isDebuggable() {
        return identity.isDebuggable();
    }

    @Override
    public boolean isOptimized() {
        return identity.isOptimized();
    }

    @Override
    public FileCollection getCppSource() {
        return sourceFiles;
    }

    @Override
    public FileCollection getCompileIncludePath() {
        return includePath;
    }

    @Override
    public FileCollection getLinkLibraries() {
        return linkLibraries;
    }

    public Configuration getLinkConfiguration() {
        return linkLibraries;
    }

    @Override
    public FileCollection getRuntimeLibraries() {
        return runtimeLibraries;
    }

    public Configuration getIncludePathConfiguration() {
        return includePathConfiguration;
    }

    @Override
    public TargetMachine getTargetMachine() {
        return targetPlatform.getTargetMachine();
    }

    @Override
    public CppPlatform getTargetPlatform() {
        return targetPlatform;
    }

    public NativePlatform getNativePlatform() {
        return ((DefaultCppPlatform) targetPlatform).getNativePlatform();
    }

    @Override
    public NativeToolChainInternal getToolChain() {
        return toolChain;
    }

    @Override
    public Property<CppCompile> getCompileTask() {
        return compileTaskProperty;
    }

    public PlatformToolProvider getPlatformToolProvider() {
        return platformToolProvider;
    }

    public NativeVariantIdentity getIdentity() {
        return identity;
    }
}
