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

package org.gradle.testfixtures

import org.gradle.api.Project
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.testfixtures.internal.ProjectBuilderImpl
import org.gradle.util.SetSystemProperties
import org.junit.Rule

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ProjectBuilderIntegrationTest extends AbstractIntegrationSpec {
    @Rule SetSystemProperties systemProperties
    @Rule HttpServer server

    Project project

    def setup() {
        System.setProperty("user.dir", temporaryFolder.testDirectory.absolutePath)
        file("settings.gradle") << """
            rootProject.name = 'test'
        """
    }

    def cleanup() {
        // unstopped instances of ProjectBuilderImpl projects leak build operations
        // causing other integration tests to sporadically fail.
        if (project != null) {
            ProjectBuilderImpl.stop(project)
        }
    }

    def "can resolve remote dependencies"() {
        def repo = new MavenHttpRepository(server, mavenRepo)
        repo.module("org.gradle", "a", "1.0").publish().allowAll()
        server.start()

        when:
        project = ProjectBuilder.builder().build()
        project.with {
            repositories {
                maven { url repo.uri }
            }
            configurations {
                compile
                runtime { extendsFrom compile }
            }
            dependencies {
                compile "org.gradle:a:1.0"
            }
        }
        def compileFiles = project.configurations.compile.files
        def runtimeFiles = project.configurations.runtime.files

        then:
        compileFiles.size() == 1
        runtimeFiles.size() == 1
    }

    def "can provide custom Gradle user home"() {
        given:
        File customGradleUserHome = temporaryFolder.createDir('gradle-user-home')

        when:
        project = ProjectBuilder.builder().withGradleUserHomeDir(customGradleUserHome).build()

        then:
        customGradleUserHome.exists()
        project.gradle.startParameter.gradleUserHomeDir == customGradleUserHome
    }

    def "can be used in parallel"() {
        def threads = 5
        def startSignal = new CountDownLatch(1)
        def doneSignal = new CountDownLatch(threads)

        when:
        threads.times {
            new Thread({
                startSignal.await()
                def spawnedProject = ProjectBuilder.builder().build()
                try {
                    doneSignal.countDown()
                } finally {
                    ProjectBuilderImpl.stop(spawnedProject)
                }

            }).start()
        }
        startSignal.countDown()
        project = ProjectBuilder.builder().build()

        then:
        doneSignal.await(60, TimeUnit.SECONDS)
    }
}
