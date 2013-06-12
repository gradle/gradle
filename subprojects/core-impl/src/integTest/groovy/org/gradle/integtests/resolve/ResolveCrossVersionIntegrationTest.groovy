/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule

class ResolveCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {
    @Rule HttpServer server = new HttpServer()

    def "can upgrade and downgrade Gradle version"() {
        given:
        mavenRepo.module("test", "io", "1.4").publish()
        mavenRepo.module("test", "lang", "2.4").publish()
        mavenRepo.module("test", "lang", "2.6").publish()

        and:
        server.start()
        server.allowGetOrHead("/repo", mavenRepo.rootDir)

        and:
        buildFile << """
repositories {
    maven { url "http://localhost:${server.port}/repo" }
}

configurations {
    compile
}

dependencies {
    compile 'test:io:1.4'
    compile 'test:lang:2.+'
}

task check << {
    assert configurations.compile*.name as Set == ['io-1.4.jar', 'lang-2.6.jar'] as Set
}
"""

        expect:
        version previous withTasks 'check' run()
        version current withTasks 'check' run()
        version previous withTasks 'check' run()
    }
}
