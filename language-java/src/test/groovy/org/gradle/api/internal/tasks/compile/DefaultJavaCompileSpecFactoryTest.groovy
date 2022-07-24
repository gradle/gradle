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

package org.gradle.api.internal.tasks.compile

import org.gradle.api.internal.file.TestFiles
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.internal.JavaToolchain
import spock.lang.Specification

class DefaultJavaCompileSpecFactoryTest extends Specification {

    def "produces correct spec with fork=#fork, executable=#executable, toolchain=#toolchainHome"() {
        CompileOptions options = new CompileOptions(Mock(ObjectFactory))
        options.fork = fork
        options.forkOptions.executable = executable
        def toolchain = null
        if (toolchainHome != null) {
            toolchain = Mock(JavaToolchain)
            toolchain.installationPath >> TestFiles.fileFactory().dir(toolchainHome)
            toolchain.languageVersion >> JavaLanguageVersion.of(8)
        }
        DefaultJavaCompileSpecFactory factory = new DefaultJavaCompileSpecFactory(options, toolchain)

        when:
        def spec = factory.create()

        then:
        spec instanceof DefaultJavaCompileSpec
        ForkingJavaCompileSpec.isAssignableFrom(spec.getClass()) == implementsForking
        CommandLineJavaCompileSpec.isAssignableFrom(spec.getClass()) == implementsCommandLine

        where:
        fork  | executable | implementsForking | implementsCommandLine | toolchainHome
        false | null       | false             | false                 | null
        true  | null       | true              | false                 | null
        true  | "X"        | false             | true                  | null
        true | "X" | true | false | File.createTempDir()
        false | null       | false             | false                 | Jvm.current().javaHome
    }

}
