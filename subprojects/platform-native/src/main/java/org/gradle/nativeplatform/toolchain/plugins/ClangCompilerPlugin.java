/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.model.Defaults;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.plugins.NativeComponentPlugin;
import org.gradle.nativeplatform.toolchain.Clang;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.clang.ClangToolChain;
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.SystemLibraryDiscovery;
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProviderFactory;
import org.gradle.process.internal.ExecActionFactory;

/**
 * A {@link Plugin} which makes the <a href="http://clang.llvm.org">Clang</a> compiler available for compiling C/C++ code.
 */
@Incubating
@NonNullApi
public class ClangCompilerPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(NativeComponentPlugin.class);
    }

    static class Rules extends RuleSource {
        @Defaults
        public static void addToolChain(NativeToolChainRegistryInternal toolChainRegistry, ServiceRegistry serviceRegistry) {
            final FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
            final ExecActionFactory execActionFactory = serviceRegistry.get(ExecActionFactory.class);
            final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory = serviceRegistry.get(CompilerOutputFileNamingSchemeFactory.class);
            final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            final BuildOperationExecutor buildOperationExecutor = serviceRegistry.get(BuildOperationExecutor.class);
            final CompilerMetaDataProviderFactory metaDataProviderFactory = serviceRegistry.get(CompilerMetaDataProviderFactory.class);
            final SystemLibraryDiscovery standardLibraryDiscovery = serviceRegistry.get(SystemLibraryDiscovery.class);
            final WorkerLeaseService workerLeaseService = serviceRegistry.get(WorkerLeaseService.class);

            toolChainRegistry.registerFactory(Clang.class, new NamedDomainObjectFactory<Clang>() {
                @Override
                public Clang create(String name) {
                    return instantiator.newInstance(ClangToolChain.class, name, buildOperationExecutor, OperatingSystem.current(), fileResolver, execActionFactory, compilerOutputFileNamingSchemeFactory, metaDataProviderFactory, standardLibraryDiscovery, instantiator, workerLeaseService);
                }
            });
            toolChainRegistry.registerDefaultToolChain(ClangToolChain.DEFAULT_NAME, Clang.class);
        }

    }
}
