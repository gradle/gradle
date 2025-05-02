/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.invocation

import org.gradle.api.JavaVersion
import org.gradle.api.initialization.Settings
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.groovy.scripts.internal.DefaultScriptCompilationHandler.ScriptClassLoader
import org.gradle.initialization.DefaultSettings
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.jvm.Jvm
import org.gradle.invocation.DefaultGradle

import java.util.concurrent.Executor
import java.util.concurrent.Executors.DefaultThreadFactory
import java.util.concurrent.ThreadFactory

class GradleLifecycleUnsupportedTypesIntegrationTest extends AbstractIntegrationSpec {

    def "reports when lifecycle action carries an object of type #baseType"() {
        given:
        settingsFile << """
            abstract class SomeBuildService implements $BuildService.name<${BuildServiceParameters.name}.None> {
            }

            class SomeBean {
                private ${baseType.name} badReference
            }

            def configureGradleLifecycle() {
                ${baseType.name} badReference
                final bean = new SomeBean()
                final beanWithSameType = new SomeBean()

                badReference = ${reference}
                bean.badReference = ${reference}
                beanWithSameType.badReference = ${reference}

                gradle.lifecycle.beforeProject {
                    println "this.reference = " + badReference?.toString()
                    println "bean.reference = " + bean.badReference?.toString()
                    println "beanWithSameType.reference = " + beanWithSameType.badReference?.toString()
                }
            }

            configureGradleLifecycle()
        """

        when:
        fails "help"

        then:
        failureCauseContains("cannot serialize object of type '$concreteTypeName'")

        where:
        concreteType                      | baseType         | reference
        // Live JVM state
        ScriptClassLoader                 | ClassLoader      | "getClass().classLoader"
        Thread                            | Thread           | "Thread.currentThread()"
        DefaultThreadFactory              | ThreadFactory    | "java.util.concurrent.Executors.defaultThreadFactory()"
        executorServiceTypeOnCurrentJvm() | Executor         | "java.util.concurrent.Executors.newSingleThreadExecutor().tap { shutdown() }"
        ByteArrayInputStream              | InputStream      | "new java.io.ByteArrayInputStream([] as byte[])"
        ByteArrayOutputStream             | OutputStream     | "new java.io.ByteArrayOutputStream()"
        FileDescriptor                    | FileDescriptor   | "FileDescriptor.in"
        RandomAccessFile                  | RandomAccessFile | "new RandomAccessFile(file('some').tap { text = '' }, 'r').tap { close() }"
        Socket                            | Socket           | "new java.net.Socket()"
        ServerSocket                      | ServerSocket     | "new java.net.ServerSocket(0).tap { close() }"

        // Gradle Build Model
        DefaultGradle                     | Gradle           | "gradle"
        DefaultSettings                   | Settings         | "settings"

        // Dependency Resolution Types
        // ????

        // direct BuildService reference, build services must always be referenced via their providers
        'SomeBuildService'                | BuildService     | "gradle.sharedServices.registerIfAbsent('service', SomeBuildService) {}.get()"

        concreteTypeName = concreteType instanceof Class ? concreteType.name : concreteType
    }

    private static executorServiceTypeOnCurrentJvm() {
        def shortName = Jvm.current().javaVersion.isCompatibleWith(JavaVersion.VERSION_21) ? 'AutoShutdownDelegatedExecutorService' : 'FinalizableDelegatedExecutorService'
        return 'java.util.concurrent.Executors$' + shortName
    }
}
