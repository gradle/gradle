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

import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.Factory
import org.gradle.internal.os.OperatingSystem
import org.gradle.plugins.binaries.BinariesPlugin
import org.gradle.plugins.binaries.model.CompilerRegistry
import org.gradle.plugins.cpp.gpp.internal.GppCompilerAdapter
import org.gradle.process.internal.DefaultExecAction
import org.gradle.process.internal.ExecAction

/**
 * A {@link Plugin} which makes the <a href="http://gcc.gnu.org/">GNU G++ compiler</a> available for compiling C/C++ code.
 */
class GppCompilerPlugin implements Plugin<ProjectInternal> {

    void apply(ProjectInternal project) {
        project.plugins.apply(BinariesPlugin)
        project.extensions.getByType(CompilerRegistry).add(new GppCompilerAdapter(
                OperatingSystem.current(),
                new Factory<ExecAction>() {
                    ExecAction create() {
                        new DefaultExecAction(project.getFileResolver())
                    }
                }))
    }

}
