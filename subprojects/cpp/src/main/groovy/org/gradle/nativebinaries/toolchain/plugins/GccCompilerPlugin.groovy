/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.internal.Factory
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.ToolChainRegistry
import org.gradle.nativebinaries.plugins.NativeBinariesPlugin
import org.gradle.nativebinaries.toolchain.Gcc
import org.gradle.nativebinaries.toolchain.internal.gcc.GccToolChain
import org.gradle.process.internal.DefaultExecAction
import org.gradle.process.internal.ExecAction

import javax.inject.Inject
/**
 * A {@link Plugin} which makes the <a href="http://gcc.gnu.org/">GNU GCC/G++ compiler</a> available for compiling C/C++ code.
 */
@Incubating
class GccCompilerPlugin implements Plugin<Project> {
    private final FileResolver fileResolver

    @Inject
    GccCompilerPlugin(FileResolver fileResolver) {
        this.fileResolver = fileResolver
    }

    void apply(Project project) {
        project.plugins.apply(NativeBinariesPlugin)

        final toolChainRegistry = project.extensions.getByType(ToolChainRegistry)
        toolChainRegistry.registerFactory(Gcc, { String name ->
            return new GccToolChain(name, OperatingSystem.current(), fileResolver, new Factory<ExecAction>() {
                ExecAction create() {
                    return new DefaultExecAction(fileResolver);
                }
            })
        })
        toolChainRegistry.registerDefaultToolChain(GccToolChain.DEFAULT_NAME, Gcc)
    }

}
