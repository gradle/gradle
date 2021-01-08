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
import org.gradle.nativeplatform.toolchain.VisualCpp;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.UcrtLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualCppToolChain;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualStudioLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.WindowsSdkLocator;
import org.gradle.process.internal.ExecActionFactory;

/**
 * A {@link Plugin} which makes the Microsoft Visual C++ compiler available to compile C/C++ code.
 */
@Incubating
public class MicrosoftVisualCppCompilerPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(NativeComponentPlugin.class);
    }

    static class Rules extends RuleSource {
        @Defaults
        public static void addToolChain(NativeToolChainRegistryInternal toolChainRegistry, ServiceRegistry serviceRegistry) {
            final FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
            final ExecActionFactory execActionFactory = serviceRegistry.get(ExecActionFactory.class);
            final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            final OperatingSystem operatingSystem = serviceRegistry.get(OperatingSystem.class);
            final BuildOperationExecutor buildOperationExecutor = serviceRegistry.get(BuildOperationExecutor.class);
            final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory = serviceRegistry.get(CompilerOutputFileNamingSchemeFactory.class);
            final VisualStudioLocator visualStudioLocator = serviceRegistry.get(VisualStudioLocator.class);
            final UcrtLocator ucrtLocator = serviceRegistry.get(UcrtLocator.class);
            final WindowsSdkLocator windowsSdkLocator = serviceRegistry.get(WindowsSdkLocator.class);
            final WorkerLeaseService workerLeaseService = serviceRegistry.get(WorkerLeaseService.class);

            toolChainRegistry.registerFactory(VisualCpp.class, new NamedDomainObjectFactory<VisualCpp>() {
                @Override
                public VisualCpp create(String name) {
                return instantiator.newInstance(VisualCppToolChain.class, name, buildOperationExecutor, operatingSystem, fileResolver, execActionFactory, compilerOutputFileNamingSchemeFactory, visualStudioLocator, windowsSdkLocator, ucrtLocator, instantiator, workerLeaseService);
                }
            });
            toolChainRegistry.registerDefaultToolChain(VisualCppToolChain.DEFAULT_NAME, VisualCpp.class);
        }

    }
}
