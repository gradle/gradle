/*
 * Copyright 2012 the original author or authors.
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



package org.gradle.nativebinaries.toolchain.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.reflect.Instantiator
import org.gradle.model.ModelRule
import org.gradle.model.ModelRules
import org.gradle.nativebinaries.plugins.NativeBinariesPlugin
import org.gradle.nativebinaries.toolchain.VisualCpp
import org.gradle.nativebinaries.toolchain.internal.ToolChainRegistryInternal
import org.gradle.nativebinaries.toolchain.internal.msvcpp.VisualCppToolChain
import org.gradle.nativebinaries.toolchain.internal.msvcpp.VisualStudioLocator
import org.gradle.nativebinaries.toolchain.internal.msvcpp.WindowsSdkLocator
import org.gradle.process.internal.ExecActionFactory

import javax.inject.Inject

/**
 * A {@link Plugin} which makes the Microsoft Visual C++ compiler available to compile C/C++ code.
 */
@Incubating
class MicrosoftVisualCppPlugin implements Plugin<Project> {
    private final FileResolver fileResolver;
    private final ExecActionFactory execActionFactory
    private final Instantiator instantiator
    private final ModelRules modelRules
    private final OperatingSystem operatingSystem
    private final VisualStudioLocator visualStudioLocator
    private final WindowsSdkLocator windowsSdkLocator

    @Inject
    MicrosoftVisualCppPlugin(FileResolver fileResolver, ExecActionFactory execActionFactory, ModelRules modelRules, Instantiator instantiator, OperatingSystem operatingSystem,
                             VisualStudioLocator visualStudioLocator, WindowsSdkLocator windowsSdkLocator) {
        this.windowsSdkLocator = windowsSdkLocator
        this.visualStudioLocator = visualStudioLocator
        this.operatingSystem = operatingSystem
        this.execActionFactory = execActionFactory
        this.fileResolver = fileResolver
        this.instantiator = instantiator
        this.modelRules = modelRules
    }

    void apply(Project project) {
        project.plugins.apply(NativeBinariesPlugin)

        modelRules.rule(new ModelRule() {
            void addToolChain(ToolChainRegistryInternal toolChainRegistry) {
                toolChainRegistry.registerFactory(VisualCpp, { String name ->
                    return instantiator.newInstance(VisualCppToolChain, name, operatingSystem, fileResolver, execActionFactory, visualStudioLocator, windowsSdkLocator)
                })
                toolChainRegistry.registerDefaultToolChain(VisualCppToolChain.DEFAULT_NAME, VisualCpp)
            }
        })
    }
}
