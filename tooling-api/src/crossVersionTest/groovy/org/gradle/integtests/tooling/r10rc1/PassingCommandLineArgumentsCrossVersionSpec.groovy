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
package org.gradle.integtests.tooling.r10rc1

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.exceptions.UnsupportedBuildArgumentException
import org.gradle.tooling.model.GradleProject

class PassingCommandLineArgumentsCrossVersionSpec extends ToolingApiSpecification {

//    We don't want to validate *all* command line options here, just enough to make sure passing through works.

    def "understands project properties for building model"() {
        given:
        toolingApi.verboseLogging = false //sanity check, see GRADLE-2226
        file("build.gradle") << """
        description = project.getProperty('theDescription')
"""

        when:
        GradleProject project = withConnection { ProjectConnection it ->
            it.model(GradleProject).withArguments('-PtheDescription=heyJoe').get()
        }

        then:
        project.description == 'heyJoe'
    }

    def "understands system properties"() {
        given:
        file("build.gradle") << """
        task printProperty {
            doLast {
                file('sysProperty.txt') << System.getProperty('sysProperty')
            }
        }
"""

        when:
        withConnection { ProjectConnection it ->
            it.newBuild().forTasks('printProperty').withArguments('-DsysProperty=welcomeToTheJungle').run()
        }

        then:
        file('sysProperty.txt').text.contains('welcomeToTheJungle')
    }

    def "can use custom build file"() {
        given:
        file("foo.gradle") << """
        task someCoolTask
"""

        when:
        withConnection { ProjectConnection it ->
            it.newBuild().forTasks('someCoolTask').withArguments('-b', 'foo.gradle').run()
        }

        then:
        noExceptionThrown()

    }

    def "can use custom log level"() {
        //logging infrastructure is not installed when running in-process to avoid issues
        toolingApi.requireDaemons()

        given:
        file("build.gradle") << """
        logger.debug("debugging stuff")
        logger.info("infoing stuff")
"""

        when:
        String debug = withBuild { it.withArguments('-d') }.standardOutput

        and:
        String info = withBuild { it.withArguments('-i') }.standardOutput

        then:
        debug.count("debugging stuff") == 1
        debug.count("infoing stuff") == 1

        and:
        info.count("debugging stuff") == 0
        info.count("infoing stuff") == 1
    }

    def "gives decent feedback for invalid option"() {
        when:
        withConnection { ProjectConnection it ->
            it.newBuild().withArguments('--foreground').run()
        }

        then:
        UnsupportedBuildArgumentException ex = thrown()
        ex.message.contains('--foreground')
    }

    def "can overwrite project dir via build arguments"() {
        given:
        file('otherDir').createDir()
        file('otherDir/build.gradle') << "assert projectDir.name.endsWith('otherDir')"
        file('otherDir/settings.gradle') << ''

        when:
        withConnection {
            it.newBuild().withArguments('-p', 'otherDir').run()
        }

        then:
        noExceptionThrown()
    }

    def "can overwrite gradle user home via build arguments"() {
        given:
        file('.myGradle').createDir()
        file('build.gradle') << "assert gradle.gradleUserHomeDir.name.endsWith('.myGradle')"
        toolingApi.requireIsolatedDaemons()

        when:
        withConnection {
            it.newBuild().withArguments('-g', '.myGradle').run()
        }

        then:
        noExceptionThrown()
    }

    def "can overwrite gradle user home via system property build argument"() {
        given:
        file('.myGradle').createDir()
        file('build.gradle') << "assert gradle.gradleUserHomeDir.name.endsWith('.myGradle')"
        toolingApi.requireIsolatedDaemons()

        when:
        withConnection {
            it.newBuild().withArguments('-Dgradle.user.home=.myGradle').run()
        }

        then:
        noExceptionThrown()
    }

    @TargetGradleVersion(">=2.6 <5.0")
    def "can configure searchUpwards via build arguments"() {
        given:
        file('build.gradle') << "assert !gradle.startParameter.searchUpwards"

        when:
        withConnection {
            it.newBuild().withArguments('-u').run()
        }

        then:
        noExceptionThrown()
    }

    def "can overwrite task names via build arguments"() {
        given:
        file('build.gradle') << """
task foo { doLast { assert false } }
task bar { doLast { assert true } }
"""

        when:
        withConnection {
            it.newBuild().forTasks('foo').withArguments('bar').run()
        }

        then:
        noExceptionThrown()
    }
}
