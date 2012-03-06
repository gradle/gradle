/*
 * Copyright 2009 the original author or authors.
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



package org.gradle.integtests.tooling.rc1

import org.gradle.integtests.tooling.fixture.MinTargetGradleVersion
import org.gradle.integtests.tooling.fixture.MinToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection
import static org.gradle.testing.internal.util.ExceptionAssert.assertThat

@MinToolingApiVersion('current')
@MinTargetGradleVersion('current')
class PassingCommandLineArgumentsIntegrationTest extends ToolingApiSpecification {

//    def setup() {
//        toolingApi.isEmbedded = false
//    }

//    We don't want to validate *all* command line options here, just enough to make sure passing through works.

    def "understands project properties"() {
        given:
        dist.file("build.gradle") << """
        task printProperty << {
            file('extraProperty.txt') << project.getProperty('extraProperty')
        }
"""

        when:
        withConnection { ProjectConnection it ->
            it.newBuild().forTasks('printProperty').withArguments('-PextraProperty=heyJoe').run()
        }

        then:
        dist.file('extraProperty.txt').text.contains('heyJoe')
    }

    def "understands system properties"() {
        given:
        dist.file("build.gradle") << """
        task printProperty << {
            file('sysProperty.txt') << System.getProperty('sysProperty')
        }
"""

        when:
        withConnection { ProjectConnection it ->
            it.newBuild().forTasks('printProperty').withArguments('-DsysProperty=welcomeToTheJungle').run()
        }

        then:
        dist.file('sysProperty.txt').text.contains('welcomeToTheJungle')
    }

    def "can use custom build file"() {
        given:
        dist.file("foo.gradle") << """
        task someCoolTask
"""

        when:
        withConnection { ProjectConnection it ->
            it.newBuild().forTasks('someCoolTask').withArguments('-b', 'foo.gradle').run()
        }

        then:
        noExceptionThrown()

    }

    def "gives decent feedback for invalid option"() {
        when:
        def ex = maybeFailWithConnection { ProjectConnection it ->
            it.newBuild().withArguments('--foreground').run()
        }

        then:
        assertThat(ex).containsInfo('--foreground')
    }
}
