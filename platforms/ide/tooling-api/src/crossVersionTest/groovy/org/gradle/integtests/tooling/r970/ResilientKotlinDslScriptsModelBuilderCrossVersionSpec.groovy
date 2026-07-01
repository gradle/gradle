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

package org.gradle.integtests.tooling.r970

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r930.KotlinDslPluginRelatedToolingApiSpecification
import org.gradle.integtests.tooling.r940.KotlinModelOnNullTargetAction
import org.gradle.tooling.BuildException

/**
 * From Gradle 9.7 onwards a failure observed during resilient model building is propagated to the client as a
 * {@link BuildException} (the build fails when it finishes), even for the Kotlin DSL scripts model. See the
 * {@code <9.7.0} versions of these tests in {@code r940.ResilientKotlinDslScriptsModelBuilderCrossVersionSpec}
 * for the previous behaviour, where the partial model was returned and the build succeeded.
 */
@ToolingApiVersion('>=9.3.0')
@TargetGradleVersion('>=9.7.0')
class ResilientKotlinDslScriptsModelBuilderCrossVersionSpec extends KotlinDslPluginRelatedToolingApiSpecification {

    private static final List<String> NO_EXTRA_PROPERTIES = []
    private static final List<String> IP_FLAGS = [
        "-Dorg.gradle.unsafe.isolated-projects=true",
    ]

    def setup() {
        settingsFile.delete()
    }

    def "resilient Kotlin DSL with null target fails the build when a project build script fails to compile #mode"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
        """
        buildFileKts << """
            broken !!!
        """

        when:
        fails {
            action(new KotlinModelOnNullTargetAction())
                .withArguments(*extraGradleProperties)
                .run()
        }

        then:
        def e = thrown(BuildException)
        collectCauseMessages(e).any { it?.contains("Script compilation error") }

        where:
        mode                       | extraGradleProperties
        ""                         | NO_EXTRA_PROPERTIES
        "with isolated projects"   | IP_FLAGS
    }

    def "resilient Kotlin DSL with null target fails the build when settings fail to compile #mode"() {
        given:
        settingsKotlinFile << """
            broken !!!
        """

        when:
        fails {
            action(new KotlinModelOnNullTargetAction())
                .withArguments(*extraGradleProperties)
                .run()
        }

        then:
        def e = thrown(BuildException)
        collectCauseMessages(e).any { it?.contains("Script compilation error") }

        where:
        mode                       | extraGradleProperties
        ""                         | NO_EXTRA_PROPERTIES
        "with isolated projects"   | IP_FLAGS
    }

    private static List<String> collectCauseMessages(Throwable throwable) {
        def messages = []
        Throwable current = throwable
        int depth = 0
        while (current != null && depth++ < 50) {
            messages << current.message
            current = current.cause
        }
        return messages
    }
}
