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
package org.gradle.plugins.cpp.gpp

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.Factory
import org.gradle.internal.os.OperatingSystem
import org.gradle.plugins.binaries.BinariesPlugin
import org.gradle.plugins.binaries.model.ToolChainRegistry
import org.gradle.plugins.cpp.gpp.internal.GppToolChain
import org.gradle.process.internal.DefaultExecAction
import org.gradle.process.internal.ExecAction

import javax.inject.Inject

/**
 * A {@link Plugin} which makes the <a href="http://gcc.gnu.org/">GNU G++ compiler</a> available for compiling C/C++ code.
 */
@Incubating
class GppCompilerPlugin implements Plugin<Project> {
    private final FileResolver fileResolver

    @Inject
    GppCompilerPlugin(FileResolver fileResolver) {
        this.fileResolver = fileResolver
    }

    void apply(Project project) {
        project.plugins.apply(BinariesPlugin)
        project.extensions.getByType(ToolChainRegistry).add(new GppToolChain(
                OperatingSystem.current(),
                new Factory<ExecAction>() {
                    ExecAction create() {
                        new DefaultExecAction(fileResolver)
                    }
                }))
    }

}
