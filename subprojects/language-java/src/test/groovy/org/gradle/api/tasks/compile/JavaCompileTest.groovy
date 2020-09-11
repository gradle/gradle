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

import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.JavaCompiler
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolChain
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
        spec.compileOptions.forkOptions.javaHome == javaHome
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
        spec.compileOptions.forkOptions.javaHome == javaHome
    }

    def "spec is configured using the toolchain compiler in-process using the current jvm as toolchain"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)
        javaCompile.setDestinationDir(new File("tmp"))
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
        spec instanceof DefaultJavaCompileSpec
        spec.compileOptions.forkOptions.javaHome == javaHome
        spec.getSourceCompatibility() == null
        spec.getTargetCompatibility() == null
    }

    def 'spec is configured with the right values for source and target compatibility when set in parallel with a toolchain'() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)
        javaCompile.setDestinationDir(new File("tmp"))
        def javaHome = Jvm.current().javaHome
        def metadata = Mock(JavaInstallationMetadata)
        def compiler = Mock(JavaCompiler)

        metadata.languageVersion >> JavaLanguageVersion.of(12)
        metadata.installationPath >> TestFiles.fileFactory().dir(javaHome)
        compiler.metadata >> metadata

        when:
        javaCompile.javaCompiler.set(compiler)
        javaCompile.sourceCompatibility = '8'
        javaCompile.targetCompatibility = '10'
        def spec = javaCompile.createSpec()

        then:
        spec.getSourceCompatibility() == '8'
        spec.getTargetCompatibility() == '10'
    }

    def 'spec is configured with the right value for release when set in parallel with a toolchain'() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)
        javaCompile.setDestinationDir(new File("tmp"))
        def javaHome = Jvm.current().javaHome
        def metadata = Mock(JavaInstallationMetadata)
        def compiler = Mock(JavaCompiler)

        metadata.languageVersion >> JavaLanguageVersion.of(12)
        metadata.installationPath >> TestFiles.fileFactory().dir(javaHome)
        compiler.metadata >> metadata

        when:
        javaCompile.javaCompiler.set(compiler)
        javaCompile.options.release.set(10)
        def spec = javaCompile.createSpec()

        then:
        spec.release == 10
    }

}
