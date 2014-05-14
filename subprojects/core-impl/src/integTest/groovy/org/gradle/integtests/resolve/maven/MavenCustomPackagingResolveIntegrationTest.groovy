/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import spock.lang.Issue

class MavenCustomPackagingResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {

    @Issue("http://issues.gradle.org/browse/GRADLE-2984")
    def "can resolve dependency with custom packaging"() {
        given:
        executer.withDeprecationChecksDisabled()

        def extension = "aar"
        m2Installation.mavenRepo().module("local", "local", "1.0").hasType(extension).hasPackaging(extension).publish()
        mavenHttpRepo.module("remote", "remote", "1.0").with {
            allowAll()
            hasType(extension).hasPackaging(extension).publish()
        }

        // Not publishing, just allowing the HTTP requests to 404
        mavenHttpRepo.module("local", "local", "1.0").allowAll()

        when:
        buildScript """
            configurations {
                local
                remote
            }
            repositories {
                mavenLocal() // there's a different code path for mavenLocal() so test it explicitly
                maven { url "$mavenHttpRepo.uri" }
            }
            dependencies {
                local "local:local:1.0"
                remote "remote:remote:1.0"
            }

            task showDeps {
                doLast {
                    println configurations.local.files // trigger resolve
                    println configurations.remote.files // trigger resolve
                }
            }
        """

        then:
        succeeds("showDeps")
    }

}
