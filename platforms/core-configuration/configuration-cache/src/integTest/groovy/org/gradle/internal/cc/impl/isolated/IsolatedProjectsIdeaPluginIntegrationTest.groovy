/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.impl.isolated

import org.gradle.util.internal.ToBeImplemented

class IsolatedProjectsIdeaPluginIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "can apply idea plugin"() {
        settingsFile << """
            include("sub")
        """
        buildFile """
            plugins { id("idea") }
        """
        buildFile "sub/build.gradle", """
            plugins { id("idea") }
        """

        when:
        isolatedProjectsRun("help")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":sub")
        }

        when:
        isolatedProjectsRun("help")

        then:
        fixture.assertStateLoaded()
    }

    @ToBeImplemented
    def "can apply idea plugin and scala plugin"() {
        settingsFile << """
            include("sub")
        """
        buildFile """
            plugins { id("idea") }
        """
        buildFile "sub/build.gradle", """
            plugins {
                id("idea")
                id("scala")
            }
        """

        when:
        withIsolatedProjects()
        fails("help")

        then:
        failureHasCause("Applying 'idea' plugin to Scala projects is not supported with Isolated Projects. Disable Isolated Projects to use this integration.")
    }
}
