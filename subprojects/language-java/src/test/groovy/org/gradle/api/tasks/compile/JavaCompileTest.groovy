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

import org.gradle.api.JavaVersion
import org.gradle.api.file.Directory
import org.gradle.api.internal.tasks.compile.CommandLineJavaCompileSpec
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.JavaCompiler
import org.gradle.jvm.toolchain.JavaInstallation
import org.gradle.jvm.toolchain.JavaToolChain
import org.gradle.jvm.toolchain.internal.JavaCompilerFactory
import org.gradle.jvm.toolchain.internal.JavaToolchain
import org.gradle.jvm.toolchain.internal.ToolchainToolFactory
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import spock.lang.Issue

@SuppressWarnings('GrDeprecatedAPIUsage')
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
        javaCompile.createSpec()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Must not use `javaCompiler` property together with (deprecated) `toolchain`"
    }

    def "disallow using custom java_home with compiler present"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)

        when:
        javaCompile.javaCompiler.set(Mock(JavaCompiler))
        javaCompile.options.forkOptions.javaHome = Mock(File)
        javaCompile.createSpec()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Must not use `javaHome` property on `ForkOptions` together with `javaCompiler` property"
    }

    def "disallow using custom exectuable with compiler present"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)

        when:
        javaCompile.javaCompiler.set(Mock(JavaCompiler))
        javaCompile.options.forkOptions.executable = "somejavac"
        javaCompile.createSpec()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Must not use `exectuable` property on `ForkOptions` together with `javaCompiler` property"
    }

    def "spec is configured using the toolchain compiler via command line"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)
        def javaHome = Jvm.current().javaHome
        def installation = Mock(JavaInstallation)
        def installDir = Mock(Directory)
        installDir.asFile >> javaHome
        installation.installationDirectory >> installDir
        installation.getJavaVersion() >> JavaVersion.VERSION_12
        def toolchain = new JavaToolchain(installation, Mock(JavaCompilerFactory), Mock(ToolchainToolFactory))
        javaCompile.setDestinationDir(new File("tmp"))

        when:
        javaCompile.javaCompiler.set(toolchain.javaCompiler)
        def spec = javaCompile.createSpec()

        then:
        spec instanceof CommandLineJavaCompileSpec
        spec.compileOptions.forkOptions.javaHome == javaHome
        spec.getSourceCompatibility() == "12"
        spec.getTargetCompatibility() == "12"
    }

}
