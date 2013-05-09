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

package org.gradle.plugins.cpp.gpp

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.compile.Compiler
import org.gradle.plugins.binaries.model.internal.DefaultNativeComponent
import org.gradle.util.HelperUtil
import spock.lang.Specification
import org.gradle.plugins.binaries.model.internal.CompileSpecFactory
import org.gradle.plugins.cpp.CppCompile

class GppCompileSpecTest extends Specification {
    final ProjectInternal project = HelperUtil.createRootProject()
    
    def "is built by the compile task"() {
        given:
        def binary = new DefaultNativeComponent("binary", project, Mock(CompileSpecFactory))
        def spec = new GppCompileSpec(binary, Mock(Compiler), project)
        def compileTask = project.tasks.create("compile", CppCompile)
        spec.configure(compileTask)

        expect:
        spec.buildDependencies.getDependencies(null) == [compileTask] as Set
    }
}
