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

import org.gradle.internal.jvm.JavaInfo
import org.gradle.testing.internal.util.Specification
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import javax.tools.JavaCompiler

import static org.gradle.util.TestPrecondition.JDK

class JdkToolsTest extends Specification {
    @Requires(JDK)
    def "can get java compiler"() {
        def compiler = JdkTools.current().systemJavaCompiler

        expect:
        compiler instanceof JavaCompiler
        compiler.class == JdkTools.current().systemJavaCompiler.class
    }

    @Requires(TestPrecondition.JDK8_OR_EARLIER)
    def "throws when no tools"() {
        when:
        new JdkTools(Mock(JavaInfo) {
            getToolsJar() >> null
            getJavaHome() >> new File('.')
        })

        then:
        thrown IllegalStateException
    }

    @Requires(TestPrecondition.JDK8_OR_EARLIER)
    def "throws when tools doesn't contain compiler"() {
        when:
        if (defaultCompilerClassAlreadyInjectedIntoExtensionClassLoader()) {
            throw new IllegalStateException()
        }
        new JdkTools(Mock(JavaInfo) {
            getToolsJar() >> new File("/nothing")
        }).systemJavaCompiler

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
