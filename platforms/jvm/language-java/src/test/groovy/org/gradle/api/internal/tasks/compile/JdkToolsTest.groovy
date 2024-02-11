/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.JavaVersion
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Specification
import spock.lang.Subject

import javax.tools.JavaCompiler

class JdkToolsTest extends Specification {
    @Subject
    JdkTools current = new JdkTools(Jvm.current(), [])

    def "can get java compiler"() {
        def compiler = current.systemJavaCompiler

        expect:
        compiler instanceof JavaCompiler
        compiler.class == current.systemJavaCompiler.class
    }

    def "throws when no tools"() {
        when:
        new JdkTools(Mock(Jvm) {
            getToolsJar() >> null
            getJavaVersion() >> JavaVersion.VERSION_1_8
            getJavaHome() >> new File('.')
        }, [])

        then:
        thrown IllegalStateException
    }

    @Requires(UnitTestPreconditions.Jdk8OrEarlier)
    def "throws when tools doesn't contain compiler"() {
        when:
        if (defaultCompilerClassAlreadyInjectedIntoExtensionClassLoader()) {
            throw new IllegalStateException()
        }
        new JdkTools(Mock(Jvm) {
            getToolsJar() >> new File("/nothing")
        }, []).systemJavaCompiler

        then:
        thrown IllegalStateException
    }

    /*
     * See https://github.com/gradle/gradle-private/issues/1299
     *
     * Previously, before this test class is executed, DEFAULT_COMPILER_IMPL_NAME may already be injected into Extension Classloader,
     * so here we check for prerequisite.
     */
    private static boolean defaultCompilerClassAlreadyInjectedIntoExtensionClassLoader() {
        try {
            ClassLoader.getSystemClassLoader().loadClass(JdkTools.DEFAULT_COMPILER_IMPL_NAME)
            return true
        } catch (ClassNotFoundException ignored) {
            return false
        }
    }
}
