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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.tasks.compile.CommandLineJavaCompileSpec
import org.gradle.api.internal.tasks.compile.ForkingJavaCompileSpec
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.JavaCompiler
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import spock.lang.Issue

class JavaCompileTest extends AbstractProjectBuilderSpec {

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

    def "disallow using custom executable with compiler present"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)

        when:
        javaCompile.javaCompiler.set(Mock(JavaCompiler))
        javaCompile.options.forkOptions.executable = "somejavac"
        javaCompile.createSpec()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Must not use `executable` property on `ForkOptions` together with `javaCompiler` property"
    }

    def "fails if custom executable does not exist"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)
        javaCompile.destinationDirectory.fileValue(new File('somewhere'))
        def invalidExecutable = "invalidExecutable"

        when:
        javaCompile.options.fork = true
        javaCompile.options.forkOptions.executable = invalidExecutable
        javaCompile.createSpec()

        then:
        def e = thrown(InvalidUserDataException)
        e.message.contains("The configured executable does not exist")
        e.message.contains(invalidExecutable)
    }

    def 'uses release property combined with toolchain compiler'() {
        def javaCompile = project.tasks.create('compileJava', JavaCompile)
        javaCompile.destinationDirectory.fileValue(new File('somewhere'))
        def javaHome = Jvm.current().javaHome
        def metadata = Mock(JavaInstallationMetadata)
        def compiler = Mock(JavaCompiler)

        metadata.languageVersion >> JavaLanguageVersion.of(15)
        metadata.installationPath >> TestFiles.fileFactory().dir(javaHome)
        compiler.metadata >> metadata

        given:
        javaCompile.javaCompiler.set(compiler)
        javaCompile.options.release.set(9)

        when:
        def spec = javaCompile.createSpec()

        then:
        spec.release == 9
        spec.getSourceCompatibility() == null
        spec.getTargetCompatibility() == null
        spec.compileOptions.forkOptions.javaHome == null
        (spec as ForkingJavaCompileSpec).javaHome == javaHome
    }

    def 'uses custom source and target compatibility combined with toolchain compiler'() {
        def javaCompile = project.tasks.create('compileJava', JavaCompile)
        javaCompile.destinationDirectory.fileValue(new File('somewhere'))
        def javaHome = Jvm.current().javaHome
        def metadata = Mock(JavaInstallationMetadata)
        def compiler = Mock(JavaCompiler)

        metadata.languageVersion >> JavaLanguageVersion.of(15)
        metadata.installationPath >> TestFiles.fileFactory().dir(javaHome)
        compiler.metadata >> metadata

        given:
        javaCompile.javaCompiler.set(compiler)
        javaCompile.setSourceCompatibility('11')
        javaCompile.setTargetCompatibility('14')

        when:
        def spec = javaCompile.createSpec()

        then:
        spec.getSourceCompatibility() == '11'
        spec.getTargetCompatibility() == '14'
        spec.compileOptions.forkOptions.javaHome == null
        (spec as ForkingJavaCompileSpec).javaHome == javaHome
    }

    def "spec is configured using the toolchain compiler in-process using the current jvm as toolchain and sets release"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)
        javaCompile.destinationDirectory.set(new File("tmp"))
        def javaHome = Jvm.current().javaHome
        def metadata = Mock(JavaInstallationMetadata)
        def compiler = Mock(JavaCompiler)

        metadata.languageVersion >> JavaLanguageVersion.of(12)
        metadata.installationPath >> TestFiles.fileFactory().dir(javaHome)
        compiler.metadata >> metadata

        when:
        javaCompile.javaCompiler.set(compiler)
        def spec = javaCompile.createSpec()

        then:
        spec.getSourceCompatibility() == "12"
        spec.getTargetCompatibility() == "12"
        spec.release == null
        spec.compileOptions.forkOptions.javaHome == null
        (spec as ForkingJavaCompileSpec).javaHome == javaHome
    }

    @Issue('https://bugs.openjdk.java.net/browse/JDK-8139607')
    def "spec is configured using the toolchain compiler in-process using the current jvm as toolchain and does not set release for Java 9"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)
        javaCompile.destinationDirectory.set(new File("tmp"))
        def javaHome = Jvm.current().javaHome
        def metadata = Mock(JavaInstallationMetadata)
        def compiler = Mock(JavaCompiler)

        metadata.languageVersion >> JavaLanguageVersion.of(9)
        metadata.installationPath >> TestFiles.fileFactory().dir(javaHome)
        compiler.metadata >> metadata

        when:
        javaCompile.javaCompiler.set(compiler)
        def spec = javaCompile.createSpec()

        then:
        spec.getSourceCompatibility() == '9'
        spec.getTargetCompatibility() == '9'
        spec.release == null
        spec.compileOptions.forkOptions.javaHome == null
        (spec as ForkingJavaCompileSpec).javaHome == javaHome
    }

    def "spec is configured using the toolchain compiler in-process using the current jvm as toolchain and set source and target compatibility"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)
        javaCompile.destinationDirectory.set(new File("tmp"))
        def javaHome = Jvm.current().javaHome
        def metadata = Mock(JavaInstallationMetadata)
        def compiler = Mock(JavaCompiler)

        metadata.languageVersion >> JavaLanguageVersion.of(8)
        metadata.installationPath >> TestFiles.fileFactory().dir(javaHome)
        compiler.metadata >> metadata

        when:
        javaCompile.javaCompiler.set(compiler)
        def spec = javaCompile.createSpec()

        then:
        spec.getSourceCompatibility() == '8'
        spec.getTargetCompatibility() == '8'
        spec.release == null
        spec.compileOptions.forkOptions.javaHome == null
        (spec as ForkingJavaCompileSpec).javaHome == javaHome
    }

    def "incremental compilation is enabled by default"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)

        expect:
        javaCompile.options.incremental
        javaCompile.options.incrementalAfterFailure.get() == true
    }

    def "command line compiler spec is selected when forking and executable is set"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)
        javaCompile.destinationDirectory.set(new File("tmp"))
        def executable = Jvm.current().javacExecutable.absolutePath

        when:
        javaCompile.options.fork = true
        javaCompile.options.forkOptions.executable = executable
        def spec = javaCompile.createSpec()

        then:
        spec instanceof CommandLineJavaCompileSpec
        spec.executable.absolutePath == executable
    }

    def "command line compiler spec is selected when forking and java home is set"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)
        javaCompile.destinationDirectory.set(new File("tmp"))
        def jvm = Jvm.current()
        def javaHome = jvm.javaHome

        when:
        javaCompile.options.fork = true
        javaCompile.options.forkOptions.javaHome = javaHome
        def spec = javaCompile.createSpec()

        then:
        spec instanceof CommandLineJavaCompileSpec
        spec.executable.absolutePath == jvm.javacExecutable.absolutePath
    }

    def "java home takes precedence over executable when forking"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)
        javaCompile.destinationDirectory.set(new File("tmp"))
        def jvm = Jvm.current()
        def javaHome = jvm.javaHome

        when:
        javaCompile.options.fork = true
        javaCompile.options.forkOptions.executable = "/custom/executable/path"
        javaCompile.options.forkOptions.javaHome = javaHome
        def spec = javaCompile.createSpec()

        then:
        spec instanceof CommandLineJavaCompileSpec
        spec.executable.absolutePath == jvm.javacExecutable.absolutePath
    }
}
