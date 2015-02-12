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

package org.gradle.language.scala.internal.toolchain

import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager
import org.gradle.language.base.internal.compile.CompileSpec
import spock.lang.Specification

class DefaultScalaToolProviderTest extends Specification {
    FileResolver fileResolver = Mock()
    CompilerDaemonManager compilerDaemonManager = Mock()
    Set<File> scalacClasspath = Mock()
    Set<File> zincClasspath = Mock()
    ProjectFinder projectFinder = Mock()

    def "newCompiler provides decent error for unsupported CompileSpec"() {
        setup:
        DefaultScalaToolProvider scalaToolProvider = new DefaultScalaToolProvider(projectFinder, compilerDaemonManager, scalacClasspath, zincClasspath)

        when:
        scalaToolProvider.newCompiler(UnknownCompileSpec.class)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "Cannot create Compiler for unsupported CompileSpec type 'UnknownCompileSpec'"
    }
}

class UnknownCompileSpec implements CompileSpec {}

