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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Buildable;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.file.collections.FileCollectionAdapter;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.cpp.internal.NativeDependencyCache;
import org.gradle.language.cpp.internal.NativeVariantIdentity;
import org.gradle.language.internal.DefaultNativeBinary;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.language.swift.SwiftPlatform;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.nativeplatform.MachineArchitecture;
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.internal.modulemap.ModuleMap;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Map;
import java.util.Set;

import static org.gradle.language.cpp.CppBinary.DEBUGGABLE_ATTRIBUTE;
import static org.gradle.language.cpp.CppBinary.OPTIMIZED_ATTRIBUTE;

public class DefaultSwiftBinary extends DefaultNativeBinary implements SwiftBinary {
    private final NativeVariantIdentity identity;
    private final Provider<String> module;
    private final boolean testable;
    private final FileCollection source;
    private final FileCollection compileModules;
    private final Configuration linkLibs;
    private final Configuration runtimeLibs;
    private final RegularFileProperty moduleFile;
    private final Property<SwiftCompile> compileTaskProperty;
    private final SwiftPlatform targetPlatform;
    private final NativeToolChainInternal toolChain;
    private final PlatformToolProvider platformToolProvider;
    private final Configuration importPathConfiguration;

    public DefaultSwiftBinary(Names names, final ObjectFactory objectFactory, TaskDependencyFactory taskDependencyFactory, Provider<String> module, boolean testable, FileCollection source, ConfigurationContainer configurations, Configuration componentImplementation, SwiftPlatform targetPlatform, NativeToolChainInternal toolChain, PlatformToolProvider platformToolProvider, NativeVariantIdentity identity) {
        super(names, objectFactory, componentImplementation);
        this.module = module;
        this.testable = testable;
        this.source = source;
        this.moduleFile = objectFactory.fileProperty();
        this.compileTaskProperty = objectFactory.property(SwiftCompile.class);
        this.targetPlatform = targetPlatform;
        this.toolChain = toolChain;
        this.platformToolProvider = platformToolProvider;

        // TODO - reduce duplication with C++ binary
        RoleBasedConfigurationContainerInternal rbConfigurations = (RoleBasedConfigurationContainerInternal) configurations;

        @SuppressWarnings("deprecation")
        Configuration ipc = rbConfigurations.resolvableDependencyScopeUnlocked(names.withPrefix("swiftCompile"));
        importPathConfiguration = ipc;
        importPathConfiguration.extendsFrom(getImplementationDependencies());
        importPathConfiguration.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
        importPathConfiguration.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, identity.isDebuggable());
        importPathConfiguration.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, identity.isOptimized());
        importPathConfiguration.getAttributes().attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, identity.getTargetMachine().getOperatingSystemFamily());
        importPathConfiguration.getAttributes().attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, identity.getTargetMachine().getArchitecture());

        @SuppressWarnings("deprecation")
        Configuration nativeLink = rbConfigurations.resolvableDependencyScopeUnlocked(names.withPrefix("nativeLink"));
        nativeLink.extendsFrom(getImplementationDependencies());
        nativeLink.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.NATIVE_LINK));
        nativeLink.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, identity.isDebuggable());
        nativeLink.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, identity.isOptimized());
        nativeLink.getAttributes().attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, identity.getTargetMachine().getOperatingSystemFamily());
        nativeLink.getAttributes().attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, identity.getTargetMachine().getArchitecture());

        Configuration nativeRuntime = rbConfigurations.migratingUnlocked(names.withPrefix("nativeRuntime"), ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_RESOLVABLE);
        nativeRuntime.extendsFrom(getImplementationDependencies());
        nativeRuntime.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME));
        nativeRuntime.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, identity.isDebuggable());
        nativeRuntime.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, identity.isOptimized());
        nativeRuntime.getAttributes().attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, identity.getTargetMachine().getOperatingSystemFamily());
        nativeRuntime.getAttributes().attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, identity.getTargetMachine().getArchitecture());

        compileModules = new FileCollectionAdapter(new ModulePath(importPathConfiguration), taskDependencyFactory);
        linkLibs = nativeLink;
        runtimeLibs = nativeRuntime;
        this.identity = identity;
    }

    @Override
    public Provider<String> getModule() {
        return module;
    }

    @Override
    public Provider<String> getBaseName() {
        return module;
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
    public boolean isTestable() {
        return testable;
    }

    @Override
    public FileCollection getSwiftSource() {
        return source;
    }

    @Override
    public FileCollection getCompileModules() {
        return compileModules;
    }

    @Override
    public FileCollection getLinkLibraries() {
        return linkLibs;
    }

    public Configuration getLinkConfiguration() {
        return linkLibs;
    }

    @Override
    public FileCollection getRuntimeLibraries() {
        return runtimeLibs;
    }

    @Override
    public RegularFileProperty getModuleFile() {
        return moduleFile;
    }

    public Configuration getImportPathConfiguration() {
        return importPathConfiguration;
    }

    @Override
    public Property<SwiftCompile> getCompileTask() {
        return compileTaskProperty;
    }

    @Override
    public TargetMachine getTargetMachine() {
        return targetPlatform.getTargetMachine();
    }

    @Override
    public SwiftPlatform getTargetPlatform() {
        return targetPlatform;
    }

    public NativePlatform getNativePlatform() {
        return ((DefaultSwiftPlatform) targetPlatform).getNativePlatform();
    }

    @Override
    public NativeToolChainInternal getToolChain() {
        return toolChain;
    }

    public PlatformToolProvider getPlatformToolProvider() {
        return platformToolProvider;
    }

    @Inject
    protected NativeDependencyCache getNativeDependencyCache() {
        throw new UnsupportedOperationException();
    }

    public NativeVariantIdentity getIdentity() {
        return identity;
    }

    private class ModulePath implements MinimalFileSet, Buildable {
        private final Configuration importPathConfig;

        private Set<File> result;

        ModulePath(Configuration importPathConfig) {
            this.importPathConfig = importPathConfig;
        }

        @Override
        public String getDisplayName() {
            return "Module include path for " + DefaultSwiftBinary.this.toString();
        }

        @Override
        public Set<File> getFiles() {
            if (result == null) {
                result = Sets.newLinkedHashSet();
                Map<ComponentIdentifier, ModuleMap> moduleMaps = Maps.newLinkedHashMap();
                for (ResolvedArtifactResult artifact : importPathConfig.getIncoming().getArtifacts()) {
                    Usage cppApiUsage = getCppApiUsageFromSelectingVariant(artifact);
                    if (cppApiUsage != null) {
                        String moduleName;

                        ComponentIdentifier id = artifact.getId().getComponentIdentifier();
                        if (ModuleComponentIdentifier.class.isAssignableFrom(id.getClass())) {
                            moduleName = ((ModuleComponentIdentifier) id).getModule();
                        } else if (ProjectComponentIdentifier.class.isAssignableFrom(id.getClass())) {
                            moduleName = ((ProjectComponentIdentifier) id).getProjectName();
                        } else {
                            throw new IllegalArgumentException("Could not determine the name of " + id.getDisplayName() + ": unknown component identifier type: " + id.getClass().getSimpleName());
                        }

                        ModuleMap moduleMap;
                        if (moduleMaps.containsKey(id)) {
                            moduleMap = moduleMaps.get(id);
                        } else {
                            moduleMap = new ModuleMap(moduleName, Lists.<String>newArrayList());
                            moduleMaps.put(id, moduleMap);
                        }
                        moduleMap.getPublicHeaderPaths().add(artifact.getFile().getAbsolutePath());
                    }
                    // TODO Change this to only add SWIFT_API artifacts and instead parse modulemaps to discover compile task inputs
                    result.add(artifact.getFile());
                }

                if (!moduleMaps.isEmpty()) {
                    NativeDependencyCache cache = getNativeDependencyCache();
                    for (ModuleMap moduleMap : moduleMaps.values()) {
                        result.add(cache.getModuleMapFile(moduleMap));
                    }
                }
            }
            return result;
        }

        @Nullable
        private Usage getCppApiUsageFromSelectingVariant(ResolvedArtifactResult artifact) {
            // The first filter makes sure none of the potential NPEs that follow are impossible
            return artifact.getVariants().stream().filter(variant -> variant.getAttributes().getAttribute(Usage.USAGE_ATTRIBUTE) != null)
                .filter(variant -> variant.getAttributes().getAttribute(Usage.USAGE_ATTRIBUTE).getName().equals(Usage.C_PLUS_PLUS_API))
                .map(variant -> variant.getAttributes().getAttribute(Usage.USAGE_ATTRIBUTE))
                .findFirst().orElse(null);
        }

        @Override
        public TaskDependency getBuildDependencies() {
            return importPathConfig.getBuildDependencies();
        }
    }
}
