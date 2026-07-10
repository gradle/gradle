/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.fixtures.polyglot

import groovy.transform.CompileStatic
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.TestFile

@CompileStatic
class PolyglotProjectBuilder {
    private final GradleDsl dsl
    private final TestFile targetDirectory
    private final Map<String, BuildFileBuilder> buildFiles = [:].withDefault { new BuildFileBuilder(it.toString()) }

    @Delegate
    private final BuildFileBuilder mainBuildFile

    PolyglotProjectBuilder(GradleDsl dsl, TestFile targetDirectory) {
        this.dsl = dsl
        this.targetDirectory = targetDirectory
        this.mainBuildFile = this.buildFiles['build']
    }

    PolyglotProjectBuilder buildFile(String name, @DelegatesTo(value=BuildFileBuilder, strategy = Closure.DELEGATE_FIRST) Closure<?> conf) {
        BuilderSupport.applyConfiguration(conf, buildFiles[name])
        this
    }

    void generate() {
        buildFiles.each { id, builder ->
            builder.generate(dsl, targetDirectory)
        }
    }
}
