/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.compile

import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.internal.JavaToolchain
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultJavaCompileSpecFactoryTest extends Specification {

    def "produces correct spec with fork=#fork, executable=#executable, toolchain=#toolchain"() {
        given:
        CompileOptions options = TestUtil.newInstance(CompileOptions, TestUtil.objectFactory())
        options.fork = fork
        options.forkOptions.executable = executable ? Jvm.current().javacExecutable.absolutePath : null

        def javaToolchain = Mock(JavaToolchain) {
            getInstallationPath() >> TestFiles.fileFactory().dir(Jvm.current().javaHome)
            isCurrentJvm() >> (toolchain == null)
            getLanguageVersion() >> JavaLanguageVersion.of(toolchain ?: "8")
        }

        when:
        DefaultJavaCompileSpecFactory factory = new DefaultJavaCompileSpecFactory(options, javaToolchain)
        def spec = factory.create()

        then:
        spec instanceof DefaultJavaCompileSpec
        ForkingJavaCompileSpec.isAssignableFrom(spec.getClass()) == implementsForking
        CommandLineJavaCompileSpec.isAssignableFrom(spec.getClass()) == implementsCommandLine

        where:
        // Where the toolchain == null, we use the current JVM
        fork  | executable | toolchain | implementsForking | implementsCommandLine
        false | false      | null      | false             | false
        false | false      | "11"      | true              | false
        // Below Java 8 toolchain compiler always runs via command-line
        false | false      | "7"       | false             | true
        true  | false      | null      | true              | false
        true  | true       | null      | false             | true
        true  | true       | "11"      | false             | true
    }

}
