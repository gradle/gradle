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

package org.gradle.integtests.resolve.artifactreuse

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Issue

class ArtifactResolutionQueryIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()
    }

    @Issue('https://github.com/gradle/gradle/issues/3579')
    @IntegrationTestTimeout(60)
    def 'can use artifact resolution queries in parallel to file resolution'() {
        given:
        def module = mavenHttpRepo.module('group', "artifact", '1.0').publish()
        def handler = server.expectConcurrentAndBlock(server.get(module.pom.path).sendFile(module.pom.file), server.get('/sync'))
        server.expect(server.get(module.artifact.path).sendFile(module.artifact.file))

        settingsFile << 'include "query", "resolve"'
        buildFile << """ 
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier 
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier as DMI

allprojects {
    apply plugin: 'java'
    repositories {
       maven { url '${server.uri}/repo' }
    }
    
    dependencies {
        compile 'group:artifact:1.0'
    }
}

project('query') {
    task query {
        doLast {
            '${server.uri}/sync'.toURL().text
            dependencies.createArtifactResolutionQuery()
                        .forComponents(new DefaultModuleComponentIdentifier(DMI.newId('group','artifact'),'1.0'))
                        .withArtifacts(JvmLibrary)
                        .execute()
        }
    }    
}

project('resolve') {
    task resolve {
        doLast {
            configurations.compile.files.collect { it.file }
        }
    }  
}
"""
        executer.requireOwnGradleUserHomeDir().requireIsolatedDaemons()

        expect:
        def build = executer.withArguments('query:query', ':resolve:resolve', '--parallel').start()

        handler.waitForAllPendingCalls()
        handler.release('/sync')
        Thread.sleep(1000)
        handler.release(module.pom.path)

        build.waitForFinish()
    }
}
