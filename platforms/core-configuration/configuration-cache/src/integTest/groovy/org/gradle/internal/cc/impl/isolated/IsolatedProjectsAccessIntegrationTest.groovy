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

package org.gradle.internal.cc.impl.isolated

import org.gradle.util.internal.ToBeImplemented

class IsolatedProjectsAccessIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    @ToBeImplemented("https://github.com/gradle/gradle/issues/37643")
    def "reports problem when accessing mutable state on a project in hierarchy"() {
        settingsFile """
            include(":sub:par")
        """

        buildFile "sub/par/build.gradle", """
            rootProject.findProperty("prop")
        """

        when:
        isolatedProjectsFails("help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":sub", ":sub:par")
            // TODO:isolated this reported IP problem should not be reported
            problem("Build file 'sub/par/build.gradle': line 2: Project ':sub' cannot access 'Project.findProperty' functionality on another project ':'", 1)
            problem("Build file 'sub/par/build.gradle': line 2: Project ':sub:par' cannot access 'Project.findProperty' functionality on another project ':'", 1)
        }
    }
}
