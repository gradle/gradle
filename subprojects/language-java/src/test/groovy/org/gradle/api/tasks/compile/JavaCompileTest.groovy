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
import org.gradle.api.JavaVersion
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.provider.AbstractProperty
import org.gradle.api.internal.tasks.compile.CommandLineJavaCompileSpec
import org.gradle.api.internal.tasks.compile.ForkingJavaCompileSpec
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.JavaCompiler
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil
import spock.lang.Issue

class JavaCompileTest extends AbstractProjectBuilderSpec {

    def "uses current JVM toolchain compiler as convention and sets source and target compatibility"() {
        def javaCompile = project.tasks.create('compileJava', JavaCompile)
        javaCompile.destinationDirectory.fileValue(new File('tmp'))
        def javaHome = Jvm.current().javaHome

        when:
        def spec = javaCompile.createSpec()
        def actualCompiler = javaCompile.javaCompiler.get()

        then:
        spec.sourceCompatibility == Jvm.current().javaVersion.toString()
        spec.targetCompatibility == Jvm.current().javaVersion.toString()
        spec.compileOptions.forkOptions.javaHome == null
        spec.compileOptions.forkOptions.executable == null
        actualCompiler.metadata.installationPath.toString() == javaHome.toString()
    }

    def "uses toolchain compiler over custom executable"() {
        def javaCompile = project.tasks.create('compileJava', JavaCompile)
        javaCompile.destinationDirectory.fileValue(new File('tmp'))
        def javaHome = Jvm.current().javaHome
        def metadata = Mock(JavaInstallationMetadata)
        def compiler = Mock(JavaCompiler)

        metadata.languageVersion >> JavaLanguageVersion.of(15)
        metadata.installationPath >> TestFiles.fileFactory().dir(javaHome)
        compiler.metadata >> metadata

        given:
        javaCompile.javaCompiler.set(compiler)
        javaCompile.options.forkOptions.executable = "/custom/executable/path"

        when:
        def spec = javaCompile.createSpec()
        def actualCompiler = javaCompile.javaCompiler.get()

        then:
        spec.compileOptions.forkOptions.javaHome == null
        spec.compileOptions.forkOptions.executable == "/custom/executable/path"
        actualCompiler.metadata.installationPath.toString() == javaHome.toString()
    }

    def "uses toolchain compiler over custom java_home"() {
        def javaCompile = project.tasks.create('compileJava', JavaCompile)
        javaCompile.destinationDirectory.fileValue(new File('tmp'))
        def javaHome = Jvm.current().javaHome
        def metadata = Mock(JavaInstallationMetadata)
        def compiler = Mock(JavaCompiler)

        metadata.languageVersion >> JavaLanguageVersion.of(15)
        metadata.installationPath >> TestFiles.fileFactory().dir(javaHome)
        compiler.metadata >> metadata
        def customJavaHome = new File("custom/java_home/path")

        given:
        javaCompile.javaCompiler.set(compiler)
        javaCompile.options.forkOptions.javaHome = customJavaHome

        when:
        def spec = javaCompile.createSpec()
        def actualCompiler = javaCompile.javaCompiler.get()

        then:
        spec.compileOptions.forkOptions.javaHome == customJavaHome
        spec.compileOptions.forkOptions.executable == null
        actualCompiler.metadata.installationPath.toString() == javaHome.toString()
    }

    def "uses custom java_home over custom executable"() {
        def javaCompile = project.tasks.create('compileJava', JavaCompile)
        javaCompile.destinationDirectory.fileValue(new File('tmp'))
        def javaHome = Jvm.current().javaHome

        given:
        javaCompile.options.forkOptions.javaHome = javaHome
        javaCompile.options.forkOptions.executable = "/custom/executable/path"

        when:
        def spec = javaCompile.createSpec()
        def actualCompiler = javaCompile.javaCompiler.get()

        then:
        spec.compileOptions.forkOptions.javaHome == javaHome
        spec.compileOptions.forkOptions.executable == "/custom/executable/path"
        actualCompiler.metadata.installationPath.toString() == javaHome.toString()
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
        def e = thrown(AbstractProperty.PropertyQueryException)
        def cause = TestUtil.getRootCause(e) as InvalidUserDataException
        cause.message.contains("The configured executable does not exist")
        cause.message.contains(invalidExecutable)
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
        spec.sourceCompatibility == null
        spec.targetCompatibility == null
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
        spec.sourceCompatibility == '11'
        spec.targetCompatibility == '14'
        spec.compileOptions.forkOptions.javaHome == null
        (spec as ForkingJavaCompileSpec).javaHome == javaHome
    }

    def 'source compatibility serves as target compatibility fallback on compile spec'() {
        def javaCompile = project.tasks.create('compileJava', JavaCompile)
        javaCompile.destinationDirectory.fileValue(new File('somewhere'))

        def prevJavaVersion = JavaVersion.toVersion(Jvm.current().javaVersion.majorVersion.toInteger() - 1).toString()

        given:
        javaCompile.setSourceCompatibility(prevJavaVersion)

        when:
        def spec = javaCompile.createSpec()

        then:
        spec.sourceCompatibility == prevJavaVersion
        spec.targetCompatibility == prevJavaVersion
    }

    def 'source compatibility serves as target compatibility fallback on compile spec when compiler tool is defined'() {
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

        when:
        def spec = javaCompile.createSpec()

        then:
        spec.sourceCompatibility == '11'
        spec.targetCompatibility == '11'
    }

    @Issue('https://bugs.openjdk.java.net/browse/JDK-8139607')
    def "configuring toolchain compiler sets source and target compatibility on the compile spec"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)
        javaCompile.destinationDirectory.set(new File("tmp"))
        def javaHome = Jvm.current().javaHome
        def metadata = Mock(JavaInstallationMetadata)
        def compiler = Mock(JavaCompiler)

        metadata.languageVersion >> JavaLanguageVersion.of(toolchainVersion)
        metadata.installationPath >> TestFiles.fileFactory().dir(javaHome)
        compiler.metadata >> metadata

        when:
        javaCompile.javaCompiler.set(compiler)
        def spec = javaCompile.createSpec()

        then:
        spec.sourceCompatibility == JavaVersion.toVersion(toolchainVersion).toString()
        spec.targetCompatibility == JavaVersion.toVersion(toolchainVersion).toString()
        spec.release == null
        spec.compileOptions.forkOptions.javaHome == null
        (spec as ForkingJavaCompileSpec).javaHome == javaHome

        where:
        toolchainVersion << [8, 9, 10, 11]
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
}
