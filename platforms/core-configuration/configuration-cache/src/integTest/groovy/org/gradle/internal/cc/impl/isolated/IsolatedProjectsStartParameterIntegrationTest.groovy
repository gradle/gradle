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

package org.gradle.internal.cc.impl.isolated

import spock.lang.Issue

class IsolatedProjectsStartParameterIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "mutating StartParameter method from root project build script is a violation"() {
        buildFile("""
            gradle.startParameter.${call}
        """)

        when:
        isolatedProjectsFailsUsing(mode, "help")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":")
            problem("Build file 'build.gradle': line 2: Cannot call '${method}' on StartParameter after settings have been evaluated when Isolated Projects is enabled.")
        }

        where:
        method                              | call
        "setDryRun(boolean)"                | 'setDryRun(true)'
        "setBuildCacheEnabled(boolean)"     | 'setBuildCacheEnabled(true)'
        "setOffline(boolean)"               | 'setOffline(true)'

        combined:
        mode << ALL_MODES
    }

    def "mutating StartParameterInternal method from root project build script is a violation"() {
        buildFile("""
            gradle.startParameter.${call}
        """)

        when:
        isolatedProjectsFailsUsing(mode, "help")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":")
            problem("Build file 'build.gradle': line 2: Cannot call '${method}' on StartParameter after settings have been evaluated when Isolated Projects is enabled.")
        }

        where:
        method                                      | call
        "setConfigurationCacheDebug(boolean)"       | 'setConfigurationCacheDebug(true)'
        "setVfsVerboseLogging(boolean)"             | 'setVfsVerboseLogging(true)'

        combined:
        mode << ALL_MODES
    }

    def "mutating StartParameter from included build project script is a violation"() {
        settingsFile("""
            includeBuild("included")
        """)
        file("included/build.gradle") << """
            gradle.startParameter.setOffline(true)
        """

        when:
        isolatedProjectsFailsUsing(mode, "help")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":", ":included")
            problem("Build file 'included/build.gradle': line 2: Cannot call 'setOffline(boolean)' on StartParameter after settings have been evaluated when Isolated Projects is enabled.")
        }

        where:
        mode << ALL_MODES
    }

    def "mutating StartParameter from root build settings script is allowed"() {
        settingsFile("""
            gradle.startParameter.setDryRun(true)
        """)

        when:
        isolatedProjectsRun("help")

        then:
        fixture.assertStateStored {
            projectsConfigured(":")
        }
    }

    def "mutating StartParameter from included build settings script is allowed"() {
        settingsFile("""
            includeBuild("included")
        """)
        file("included/settings.gradle") << """
            gradle.startParameter.setDryRun(true)
        """

        when:
        isolatedProjectsRun("help")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":included")
        }
    }

    def "mutating parent build StartParameter from included build project script is a violation"() {
        settingsFile("""
            includeBuild("included")
        """)
        file("included/build.gradle") << """
            gradle.parent.startParameter.setDryRun(true)
        """

        when:
        isolatedProjectsFailsUsing(mode, "help")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":", ":included")
            problem("Build file 'included/build.gradle': line 2: Cannot call 'setDryRun(boolean)' on StartParameter after settings have been evaluated when Isolated Projects is enabled.")
        }

        where:
        mode << ALL_MODES
    }

    @Issue("https://github.com/gradle/gradle/issues/37944")
    def "build with buildSrc does not report StartParameter violations"() {
        // Gradle internally configures the buildSrc build's task requests; this must not be
        // reported as a StartParameter mutation (it used to mutate them after settings evaluation).
        file("buildSrc/build.gradle") << """
            plugins { id 'java' }
        """
        file("buildSrc/src/main/java/Dummy.java") << "public class Dummy {}"
        buildFile("""
            tasks.register("ok")
        """)

        when:
        isolatedProjectsRun("ok")

        then:
        fixture.assertStateStored {
            projectsConfigured(":buildSrc", ":")
        }
    }
    @spock.lang.Issue("https://github.com/gradle/gradle/issues/37944")
    def "build relying on default tasks does not report StartParameter violations"() {
        // Gradle internally resolves the default-tasks request at scheduling time; this must not
        // mutate the StartParameter (it used to write the resolved task names back into it).
        buildFile("""
            defaultTasks 'ok'
            tasks.register('ok') { doLast { println 'default task ran' } }
        """)

        when:
        isolatedProjectsRun()

        then:
        outputContains("default task ran")
        fixture.assertStateStored {
            projectsConfigured(":")
        }
    }

}
