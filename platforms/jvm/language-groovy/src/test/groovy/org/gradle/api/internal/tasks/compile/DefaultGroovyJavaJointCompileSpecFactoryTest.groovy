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
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultGroovyJavaJointCompileSpecFactoryTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "produces correct spec type" () {
        CompileOptions options = TestUtil.newInstance(CompileOptions.class, TestUtil.objectFactory())
        options.fork = fork
        options.forkOptions.executable = executable ? Jvm.current().javacExecutable.absolutePath : null
        DefaultGroovyJavaJointCompileSpecFactory factory = new DefaultGroovyJavaJointCompileSpecFactory(options, null)

        when:
        def spec = factory.create()

        then:
        spec instanceof DefaultGroovyJavaJointCompileSpec
        ForkingJavaCompileSpec.isAssignableFrom(spec.getClass()) == implementsForking
        CommandLineJavaCompileSpec.isAssignableFrom(spec.getClass()) == implementsCommandLine

        where:
        fork  | executable | implementsForking | implementsCommandLine
        false | false      | false             | false
        true  | false      | true              | false
        true  | true       | false             | true
    }

    def 'produces correct spec type for toolchains'() {
        // Make sure other Java home is valid from Jvm.forHome point of view and compiler executable exists
        def otherJavaHome = tmpDir.createDir("other-java-home")
        otherJavaHome.createDir("bin")
        otherJavaHome.file(OperatingSystem.current().getExecutableName("bin/java")).touch()
        otherJavaHome.file(OperatingSystem.current().getExecutableName("bin/javac")).touch()

        def version = currentVM == 'current' ? Jvm.current().javaVersion.majorVersion : currentVM
        def javaHome = currentVM == 'current' ? Jvm.current().javaHome : otherJavaHome.absoluteFile

        JavaInstallationMetadata metadata = Mock(JavaInstallationMetadata)
        metadata.languageVersion >> JavaLanguageVersion.of(version)
        metadata.installationPath >> TestFiles.fileFactory().dir(javaHome)
        metadata.isCurrentJvm() >> (Jvm.current().javaHome == javaHome)

        CompileOptions options = TestUtil.newInstance(CompileOptions.class, TestUtil.objectFactory())
        options.fork = fork
        DefaultGroovyJavaJointCompileSpecFactory factory = new DefaultGroovyJavaJointCompileSpecFactory(options, metadata)

        when:
        def spec = factory.create()

        then:
        spec instanceof DefaultGroovyJavaJointCompileSpec
        ForkingJavaCompileSpec.isAssignableFrom(spec.getClass()) == implementsForking
        CommandLineJavaCompileSpec.isAssignableFrom(spec.getClass()) == implementsCommandLine

        where:
        currentVM | fork  | implementsForking | implementsCommandLine
        'current' | false | false             | false
        'current' | true  | true              | false
        '7'       | false | false             | true
        '7'       | true  | false             | true
        '14'      | false | true              | false
        '14'      | true  | true              | false
    }
}
