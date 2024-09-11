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
package org.gradle.integtests.tooling.r15


import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.util.GradleVersion
import spock.lang.Issue

class CombiningCommandLineArgumentsCrossVersionSpec extends ToolingApiSpecification {

    @Issue("GRADLE-2635")
    def "can configure build file name and logging"() {
        file('buildX.gradle') << "logger.info('info message')"

        when:
        maybeExpectDeprecations()
        withBuild { it.withArguments('-b', 'buildX.gradle', '-i') }

        then:
        result.output.contains('info message')
    }

    //below was working as expected
    //the test was added to validate the behavior that was questioned on forums
    def "supports gradle.properties and changed build file"() {
        file('gradle.properties') << "systemProp.foo=bar"
        file('buildX.gradle') << "logger.lifecycle('sys property: ' + System.properties['foo'])"

        when:
        maybeExpectDeprecations()
        withBuild { it.withArguments('-b', 'buildX.gradle') }

        then:
        result.output.contains('sys property: bar')
    }

    def maybeExpectDeprecations() {
        if (targetVersion >= GradleVersion.version("7.1")) {
            def willBeRemovedIn = targetVersion >= GradleVersion.version("8.0") ? "9.0" : "8.0"
            expectDocumentedDeprecationWarning("Specifying custom build file location has been deprecated. This is scheduled to be removed in Gradle $willBeRemovedIn. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#configuring_custom_build_layout")
        }
    }
}
