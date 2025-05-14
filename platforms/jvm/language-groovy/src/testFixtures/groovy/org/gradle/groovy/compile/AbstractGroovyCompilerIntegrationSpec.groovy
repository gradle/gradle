/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.groovy.compile
import org.gradle.internal.jvm.Jvm
import org.junit.Assume
import spock.lang.Issue

import static org.gradle.util.internal.GroovyDependencyUtil.groovyModuleDependency

/**
 * Tests in this class use on disk build files - see resources/org/gradle/groovy/compile/AbstractGroovyCompilerIntegrationSpec/**
 */
abstract class AbstractGroovyCompilerIntegrationSpec extends AbstractBasicGroovyCompilerIntegrationSpec {
    def "canUseBuiltInAstTransform"() {
        if (versionLowerThan('1.6')) {
            return
        }

        when:
        run("test")

        then:
        noExceptionThrown()
    }

    def "canUseThirdPartyAstTransform"() {
        if (versionLowerThan('1.6')) {
            return
        }

        when:
        run("test")

        then:
        noExceptionThrown()
    }

    def "canUseAstTransformWrittenInGroovy"() {
        if (versionLowerThan('1.6')) {
            return
        }

        when:
        run("test")

        then:
        noExceptionThrown()
    }

    // more generally, this test is about transforms that statically reference
    // a class from the Groovy (compiler) Jar that in turn references a class from another Jar
    @Issue("GRADLE-2317")
    def canUseAstTransformThatReferencesGroovyTestCase() {
        if (versionLowerThan('3.0')) {
            return
        }

        buildFile << "dependencies { implementation '${groovyModuleDependency("groovy-test", versionNumber)}' }"
        when:
        run("test")

        then:
        noExceptionThrown()
    }

    // This is named funny to keep the path to the project
    // under Windows's limits.  This checks that we can use
    // ServletCategory as an extension class when compiling
    // Groovy code.
    @Issue("GRADLE-3235")
    def gradle3235() {
        if (versionLowerThan('2.0.5')) {
            return
        }

        when:
        run("test")

        then:
        noExceptionThrown()
    }

    def canJointCompileWithJavaCompilerExecutable() {
        Assume.assumeTrue("Setup invalid with toolchains", !getClass().name.contains('Toolchain'))
        args("-PjdkHome=${Jvm.current().getJavaHome().absolutePath}")

        expect:
        succeeds("compileGroovy")
        groovyClassFile("GroovyCode.class").exists()
        groovyClassFile("JavaCode.class").exists()
    }

    @Issue("gradle/gradle#5908")
    def "canUseAstTransformWithAsm"() {
        if (versionLowerThan('3.0')) {
            return
        }

        when:
        run("test")

        then:
        noExceptionThrown()
    }
}
