/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.tasks.compile

import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec
import org.gradle.jvm.toolchain.JavaCompiler
import org.gradle.jvm.toolchain.JavaToolChain
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import spock.lang.Issue

class JavaCompileTest extends AbstractProjectBuilderSpec {

    @Issue("https://github.com/gradle/gradle/issues/1645")
    def "can set the Java tool chain"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)
        def toolChain = Mock(JavaToolChain)
        when:
        javaCompile.setToolChain(toolChain)
        then:
        javaCompile.toolChain == toolChain
    }

    def "disallow using legacy toolchain api once compiler is present"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)

        when:
        javaCompile.javaCompiler.set(Mock(JavaCompiler))
        javaCompile.toolChain = Mock(JavaToolChain)
        javaCompile.createCompiler(Mock(DefaultJavaCompileSpec))

        then:
        def e = thrown(IllegalStateException)
        e.message == "Must not use `javaCompiler` property together with (deprecated) `toolchain`"
    }

    def "disallow using custom java_home with compiler present"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)

        when:
        javaCompile.javaCompiler.set(Mock(JavaCompiler))
        javaCompile.options.forkOptions.javaHome = Mock(File)
        javaCompile.createCompiler(Mock(DefaultJavaCompileSpec))

        then:
        def e = thrown(IllegalStateException)
        e.message == "Must not use `javaHome` property on `ForkOptions` together with `javaCompiler` property"
    }

    def "disallow using custom exectuable with compiler present"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)

        when:
        javaCompile.javaCompiler.set(Mock(JavaCompiler))
        javaCompile.options.forkOptions.executable = "somejavac"
        javaCompile.createCompiler(Mock(DefaultJavaCompileSpec))

        then:
        def e = thrown(IllegalStateException)
        e.message == "Must not use `exectuable` property on `ForkOptions` together with `javaCompiler` property"
    }
    
}
