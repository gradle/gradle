/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.maven.MavenHttpRepository
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EclipseClasspathRemoteResolutionIntegrationTest extends AbstractEclipseIntegrationTest {

    @Rule public final HttpServer server = new HttpServer()
    @Rule public final TestResources testResources = new TestResources(testDirectoryProvider)
    final def repo = new MavenHttpRepository(server, "/repo", mavenRepo)

    @Before
    void "setup"() {
        executer.requireOwnGradleUserHomeDir()
    }

    @Test
    void "does not break when source or javadoc artifacts are missing or broken"() {
//        given:
        def projectA = repo.module('group', 'projectA', '1.0').publish()
        def projectB = repo.module('group', 'projectB', '1.0').publish()
        server.start()

//        when:
        server.resetExpectations()
        projectA.pom.expectGet()
        projectA.artifact.expectGet()
        projectA.artifact(classifier: 'sources').expectGetMissing()
        projectA.artifact(classifier: 'javadoc').expectGetMissing()
        projectB.pom.expectGet()
        projectB.artifact.expectGet()
        projectB.artifact(classifier: 'sources').expectGetBroken()
        projectB.artifact(classifier: 'javadoc').expectGetBroken()

//        and:
        def result = runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'
repositories {
    maven { url "http://localhost:${server.port}/repo" }
}
dependencies {
    compile 'group:projectA:1.0', 'group:projectB:1.0'
}
eclipse {
    classpath.downloadJavadoc = true
}
"""
//        then:
        result.error == ''
    }
}
