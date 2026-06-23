/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class RepositoryHandlerMapOverloadsDeprecationIntegrationTest extends AbstractIntegrationSpec {

    private static final String FLAT_DIR_MAP_DEPRECATION = "The RepositoryHandler.flatDir(Map) method has been deprecated. " +
        "This is scheduled to be removed in Gradle 10. Use flatDir(Action) instead. " +
        "Consult the upgrading guide for further information: " +
        "https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_repository_handler_map_overloads"

    private static final String MAVEN_CENTRAL_MAP_DEPRECATION = "The RepositoryHandler.mavenCentral(Map) method has been deprecated. " +
        "This is scheduled to be removed in Gradle 10. Use mavenCentral(Action) instead. " +
        "Consult the upgrading guide for further information: " +
        "https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_repository_handler_map_overloads"

    def "flatDir(Map) emits a deprecation warning"() {
        given:
        file("repo/lib-1.0.jar").createFile()
        buildFile << """
            repositories {
                flatDir(name: 'libs', dirs: 'repo')
            }
            configurations { compile }
            dependencies {
                compile 'group:lib:1.0'
            }
            task resolve {
                def files = configurations.compile
                doLast {
                    assert files.collect { it.name } == ['lib-1.0.jar']
                }
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning(FLAT_DIR_MAP_DEPRECATION)
        succeeds("resolve")
    }

    def "mavenCentral(Map) emits a deprecation warning"() {
        given:
        buildFile << """
            repositories {
                mavenCentral(name: 'central')
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning(MAVEN_CENTRAL_MAP_DEPRECATION)
        succeeds("help")
    }
}
