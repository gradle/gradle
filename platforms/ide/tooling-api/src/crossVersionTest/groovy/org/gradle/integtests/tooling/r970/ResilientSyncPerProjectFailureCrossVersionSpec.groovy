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
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.model.GradleProject

@ToolingApiVersion('>=9.3.0')
@TargetGradleVersion('>=9.7.0')
class ResilientSyncPerProjectFailureCrossVersionSpec extends ToolingApiSpecification {

    private static final String IP_ENABLED = "-Dorg.gradle.isolated-projects=true"
    private static final String CONFIGURE_ON_DEMAND_ENABLED = "-Dorg.gradle.internal.isolated-projects.configure-on-demand=true"

    def setup() {
        settingsFile << "rootProject.name = \"root\"\n"
    }

    def "resilient sync isolates each sibling project's configuration failure (#description, #mode)"() {
        given:
        project(":p0", p0Broken)
        project(":p1", p1Broken)

        when:
        def result = fetchPerProjectFailures(extraArguments)

        then:
        // Eager configuration (configure-on-demand off) should report the same per-project failures that
        // configure-on-demand already reports today: each project sees only failures originating from itself.
        failureSourcesOf(result, ":p0") == p0Sees as Set
        failureSourcesOf(result, ":p1") == p1Sees as Set

        where:
        description           | p0Broken | p1Broken || p0Sees  | p1Sees
        "nothing broken"      | false    | false    || []      | []
        "only :p0 broken"     | true     | false    || [":p0"] | []
        "both broken"         | true     | true     || [":p0"] | [":p1"]
        combined:
        mode                  | extraArguments
        "eager"               | [IP_ENABLED]
        "configure-on-demand" | [IP_ENABLED, CONFIGURE_ON_DEMAND_ENABLED]
    }

    def "resilient sync isolates a nested project's configuration failure (#description, #mode)"() {
        given:
        project(":p0", parentBroken)
        project(":p0:p00", childBroken)

        when:
        def result = fetchPerProjectFailures(extraArguments)

        then:
        failureSourcesOf(result, ":p0") == parentSees as Set
        failureSourcesOf(result, ":p0:p00") == childSees as Set

        where:
        description           | parentBroken | childBroken || parentSees | childSees
        "nothing broken"      | false        | false       || []         | []
        "parent broken"       | true         | false       || [":p0"]    | [":p0"]
        "child broken"        | false        | true        || []         | [":p0:p00"]
        "both broken"         | true         | true        || [":p0"]    | [":p0"]
        combined:
        mode                  | extraArguments
        "eager"               | [IP_ENABLED]
        "configure-on-demand" | [IP_ENABLED, CONFIGURE_ON_DEMAND_ENABLED]
    }

    private fetchPerProjectFailures(List<String> extraArguments) {
        succeeds {
            action(new FetchPerProjectFailuresAction(GradleProject))
                .withArguments(*extraArguments)
                .run()
        }
    }

    private void project(String path, boolean broken) {
        def projectDir = createProject(path)
        if (broken) {
            projectDir.file("build.gradle") << /throw new RuntimeException("FAILURE(${path})")/
        }
    }

    // Adds the project to the settings file and creates its directory, including any parent project
    // directories (a project with no directory is an Isolated Projects violation).
    private TestFile createProject(String path) {
        settingsFile << "include('${path.substring(1)}')\n"
        def projectDir = file(path.substring(1).split(':'))
        projectDir.createDir()
        return projectDir
    }

    private static Set<String> failureSourcesOf(result, String projectPath) {
        def messages = result.failureMessagesByProject[projectPath]
        return result.failureMessagesByProject.keySet().findAll { source ->
            messages.any { it?.contains("FAILURE($source)") }
        } as Set
    }
}
