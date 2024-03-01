/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache.isolated

class IsolatedProjectsJavaIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {
    def "can build library with dependency on another library"() {
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle") << """
            plugins { id('java-library') }
        """
        file("b/build.gradle") << """
            plugins { id('java-library') }
            dependencies { implementation project(':a') }
        """

        when:
        isolatedProjectsRun("b:assemble")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a", ":b")
        }

        when:
        isolatedProjectsRun("b:assemble")

        then:
        fixture.assertStateLoaded()
    }
}
