/*
 * Copyright 2013 the original author or authors.
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

package org.gradle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class DeprecationHandlingIntegrationTest extends AbstractIntegrationSpec {
    def "reports first usage of deprecated feature from a build script"() {
        buildFile << """

someFeature()
someFeature()
task broken(type: DeprecatedTask) {
    otherFeature()
}

repositories {
    mavenRepo url: 'build/repo'
}

def someFeature() {
    DeprecationLogger.nagUserOfDiscontinuedMethod("someFeature()")
}

class DeprecatedTask extends DefaultTask {
    def otherFeature() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("otherFeature()")
    }
}
"""

        when:
        executer.withDeprecationChecksDisabled()
        run()

        then:
        output.contains("Build file '$buildFile': line 3")
        output.count("The someFeature() method has been deprecated") == 1
        output.contains("Build file '$buildFile': line 6")
        output.count("The otherFeature() method has been deprecated") == 1
        output.contains("Build file '$buildFile': line 10")
        output.count("The RepositoryHandler.mavenRepo() method has been deprecated") == 1

        // Run again to ensure logging is reset
        when:
        executer.withDeprecationChecksDisabled()
        run()

        then:
        output.contains("Build file '$buildFile': line 3")
        output.count("The someFeature() method has been deprecated") == 1
        output.contains("Build file '$buildFile': line 6")
        output.count("The otherFeature() method has been deprecated") == 1
        output.contains("Build file '$buildFile': line 10")
        output.count("The RepositoryHandler.mavenRepo() method has been deprecated") == 1

        // Not shown at quiet level
        when:
        executer.withArgument("--quiet")
        run()

        then:
        output.count("The someFeature() method has been deprecated") == 0
        output.count("The otherFeature() method has been deprecated") == 0
        output.count("The RepositoryHandler.mavenRepo() method has been deprecated") == 0
        errorOutput == ""
    }

    def "reports usage of deprecated feature from an init script"() {
        def initScript = file("init.gradle") << """
allprojects {
    someFeature()
}

def someFeature() {
    DeprecationLogger.nagUserOfDiscontinuedMethod("someFeature()")
}

"""

        when:
        executer.withDeprecationChecksDisabled().usingInitScript(initScript)
        run()

        then:
        output.contains("Initialization script '$initScript': line 3")
        output.count("The someFeature() method has been deprecated") == 1
        errorOutput == ""
    }

    def "reports usage of deprecated feature from an applied script"() {
        def script = file("project.gradle") << """

def someFeature() {
    DeprecationLogger.nagUserOfDiscontinuedMethod("someFeature()")
}

someFeature()
"""
        buildFile << "allprojects { apply from: 'project.gradle' }"

        when:
        executer.withDeprecationChecksDisabled()
        run()

        then:
        output.contains("Script '$script': line 7")
        output.count("The someFeature() method has been deprecated") == 1
        errorOutput == ""
    }
}
