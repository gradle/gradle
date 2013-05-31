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



package org.gradle.nativecode.toolchain.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.Factory
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativecode.base.ToolChainRegistry
import org.gradle.nativecode.base.plugins.BinariesPlugin
import org.gradle.nativecode.toolchain.internal.msvcpp.UnavailableToolChain
import org.gradle.nativecode.toolchain.internal.msvcpp.VisualCppToolChain
import org.gradle.process.internal.DefaultExecAction
import org.gradle.process.internal.ExecAction

import javax.inject.Inject

/**
 * A {@link Plugin} which makes the Microsoft Visual C++ compiler available to compile C/C++ code.
 */
@Incubating
class MicrosoftVisualCppPlugin implements Plugin<Project> {
    private final FileResolver fileResolver;

    @Inject
    MicrosoftVisualCppPlugin(FileResolver fileResolver) {
        this.fileResolver = fileResolver
    }

    void apply(Project project) {
        project.plugins.apply(BinariesPlugin)

        def registry = project.extensions.getByType(ToolChainRegistry)

        if (!OperatingSystem.current().windows) {
            registry.add(new UnavailableToolChain())
        }

        registry.add(new VisualCppToolChain(
                OperatingSystem.current(),
                new Factory<ExecAction>() {
                    ExecAction create() {
                        new DefaultExecAction(fileResolver)
                    }
                }
        ))
    }
}
