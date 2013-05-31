/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativecode.base.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.nativecode.base.ToolChainRegistry
import org.gradle.nativecode.base.internal.BinaryCompileSpec
import org.gradle.nativecode.base.internal.LinkerSpec
import org.gradle.nativecode.base.internal.StaticLibraryArchiverSpec
import org.gradle.nativecode.base.internal.ToolChainAvailability
import org.gradle.nativecode.base.internal.ToolChainInternal
import org.gradle.api.internal.tasks.compile.Compiler

class TestCompilerPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.extensions.getByType(ToolChainRegistry).add(new ToolChainInternal() {
            ToolChainAvailability getAvailability() {
                return new ToolChainAvailability()
            }

            String getName() {
                return "test"
            }

            def <T extends BinaryCompileSpec> Compiler<T> createCompiler(Class<T> specType) {
                throw new UnsupportedOperationException()
            }

            def <T extends LinkerSpec> Compiler<T> createLinker() {
                throw new UnsupportedOperationException()
            }

            def <T extends StaticLibraryArchiverSpec> Compiler<T> createStaticLibraryArchiver() {
                throw new UnsupportedOperationException()
            }
        })
    }
}
