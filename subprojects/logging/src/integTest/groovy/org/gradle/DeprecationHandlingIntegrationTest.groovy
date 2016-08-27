/*
 * Copyright 2016 the original author or authors.
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

    //@Ignore("This test will likely break with Gradle 4.0. Find some new deprecation in that case.")
    def "jetty deprecation is detected - without full stacktrace."() {

        buildFile << """
apply plugin: 'jetty' // line 2
"""
        when:
        executer.expectDeprecationWarning().withFullDeprecationStackTraceDisabled()
        run()

        then:
        output.contains('The Jetty plugin has been deprecated')
        output.contains('build.gradle:2)')
        output.count('\tat') == 1
    }

    //@Ignore("This test will likely break with Gradle 4.0. Find some new deprecation in that case.")
    def "jetty deprecation is detected - with full stacktrace."() {

        buildFile << """
apply plugin: 'jetty' // line 2
"""
        when:
        executer.expectDeprecationWarning()
        run()

        then:
        output.contains('The Jetty plugin has been deprecated')
        output.contains('build.gradle:2)')
        output.count('\tat') > 1
    }

    //@Ignore("This test will likely break with Gradle 4.0. Find some new deprecation in that case.")
    def "upToDateWhen deprecation is detected - without full stacktrace."() {
        buildFile << """
task binZip(type: Zip) {
    outputs.file('build/some').upToDateWhen { false } // line 3
    into('some')
}
"""
        when:
        executer.expectDeprecationWarning().withFullDeprecationStackTraceDisabled()
        run()

        then:
        output.contains('build.gradle:3)')
        output.count('\tat') == 1
    }

    //@Ignore("This test will likely break with Gradle 4.0. Find some new deprecation in that case.")
    def "upToDateWhen deprecation is detected - with full stacktrace."() {
        buildFile << """
task binZip(type: Zip) {
    outputs.file('build/some').upToDateWhen { false } // line 3
    into('some')
}
"""
        when:
        executer.expectDeprecationWarning()
        run()

        then:
        output.contains('build.gradle:3)')
        output.count('\tat') > 1
    }

    def "reports first usage of deprecated feature from a build script"() {
        buildFile << """
someFeature()
someFeature()

task broken(type: DeprecatedTask) {
    otherFeature()
}

def someFeature() {
    DeprecationLogger.nagUserOfDiscontinuedMethod("someFeature()") // line 10
}

class DeprecatedTask extends DefaultTask {
    def otherFeature() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("otherFeature()") // line 15
    }
}
"""

        when:
        executer.expectDeprecationWarning()
        executer.expectDeprecationWarning()
        run()

        then:
        output.contains("Build file '$buildFile': line 10")
        output.contains("Build file '$buildFile': line 15")
        output.count("The someFeature() method has been deprecated") == 1
        output.count("The otherFeature() method has been deprecated") == 1

        // Run again to ensure logging is reset
        when:
        executer.expectDeprecationWarning()
        executer.expectDeprecationWarning()
        run()

        then:
        output.count("The someFeature() method has been deprecated") == 1
        output.count("The otherFeature() method has been deprecated") == 1

        // Not shown at quiet level
        when:
        executer.withArgument("--quiet")
        run()

        then:
        output.count("The someFeature() method has been deprecated") == 0
        output.count("The otherFeature() method has been deprecated") == 0
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
        executer.expectDeprecationWarning().usingInitScript(initScript)
        run()

        then:
        output.contains("Initialization script '$initScript': line 7")
        output.count("The someFeature() method has been deprecated") == 1
        errorOutput == ""
    }

    def "reports usage of deprecated feature from an applied script"() {
        def script = file("project.gradle") << """

def someFeature() {
    DeprecationLogger.nagUserOfDiscontinuedMethod("someFeature()") // line 4
}

someFeature()
"""
        buildFile << "allprojects { apply from: 'project.gradle' }"

        when:
        executer.expectDeprecationWarning()
        run()

        then:
        output.contains("Script '$script': line 4")
        output.count("The someFeature() method has been deprecated") == 1
        errorOutput == ""
    }

    // ######################################################################

    def "DeprecatedPlugin and DeprecatedTask - without full stacktrace."() {
        given:
        buildFile << """import org.gradle.internal.deprecated.DeprecatedPlugin
import org.gradle.internal.deprecated.DeprecatedTask

apply plugin: DeprecatedPlugin // line 4

DeprecatedTask.someFeature() // line 6
DeprecatedTask.someFeature()

task broken(type: DeprecatedTask) << {
    otherFeature() // line 10
}

"""

        when:
        executer.withFullDeprecationStackTraceDisabled()
        executer.expectDeprecationWarning()
        executer.expectDeprecationWarning()
        executer.expectDeprecationWarning()
        executer.expectDeprecationWarning() // TODO: this line should be removed
        run('deprecated', 'broken')

        then:
        output.contains('build.gradle:4)')
        output.contains('build.gradle:6)')
        output.contains('build.gradle:10)')
        output.contains('(Native Method)') // TODO: this should not be printed.

        and:
        output.count('The DeprecatedPlugin plugin has been deprecated') == 1
        output.count('The someFeature() method has been deprecated') == 1
        output.count('The otherFeature() method has been deprecated') == 1
        output.count('The deprecated task has been deprecated') == 1

        and:
        output.count('\tat') == 4 // TODO: this should be 3
    }

    // ######################################################################

    def "DeprecatedPlugin and DeprecatedTask - with full stacktrace."() {
        given:
        buildFile << """import org.gradle.internal.deprecated.DeprecatedPlugin
import org.gradle.internal.deprecated.DeprecatedTask

apply plugin: DeprecatedPlugin // line 4

DeprecatedTask.someFeature() // line 6
DeprecatedTask.someFeature()

task broken(type: DeprecatedTask) << {
    otherFeature() // line 10
}

"""

        when:
        executer.expectDeprecationWarning()
        executer.expectDeprecationWarning()
        executer.expectDeprecationWarning()
        executer.expectDeprecationWarning()
        run('deprecated', 'broken')

        then:
        output.contains('build.gradle:4)')
        output.contains('build.gradle:6)')
        output.contains('build.gradle:10)')

        and:
        output.count('The DeprecatedPlugin plugin has been deprecated') == 1
        output.count('The someFeature() method has been deprecated') == 1
        output.count('The otherFeature() method has been deprecated') == 1
        output.count('The deprecated task has been deprecated') == 1

        and:
        output.count('\tat') > 4 // this should be 3
    }

    // ######################################################################

    def "DeprecatedPlugin from init script - without full stacktrace."() {
        given:
        def initScript = file("init.gradle") << """import org.gradle.internal.deprecated.DeprecatedPlugin
allprojects {
    apply plugin: DeprecatedPlugin // line 3
}
"""

        when:
        executer.withFullDeprecationStackTraceDisabled()
        executer.expectDeprecationWarning()
        executer.usingInitScript(initScript)
        run()

        then:
        output.contains('init.gradle:3)')

        output.count('The DeprecatedPlugin plugin has been deprecated') == 1

        output.count('\tat') == 1
    }

    // ######################################################################

    def "DeprecatedPlugin from applied script - without full stacktrace."() {
        given:
        file("project.gradle") << """import org.gradle.internal.deprecated.DeprecatedPlugin
apply plugin:  DeprecatedPlugin // line 2
"""

        buildFile << """
allprojects {
    apply from: 'project.gradle' // line 3
}
"""

        when:
        executer.withFullDeprecationStackTraceDisabled()
        executer.expectDeprecationWarning()
        run()

        then:
        output.contains('project.gradle:2)')

        output.count('The DeprecatedPlugin plugin has been deprecated') == 1

        output.count('\tat') == 1
    }

    // ######################################################################

    def "DeprecatedPlugin from applied script - with full stacktrace."() {
        given:
        file("project.gradle") << """import org.gradle.internal.deprecated.DeprecatedPlugin
apply plugin:  DeprecatedPlugin // line 2
"""

        buildFile << """
allprojects {
    apply from: 'project.gradle' // line 3
}
"""

        when:
        executer.expectDeprecationWarning()
        run()

        then:
        output.contains('project.gradle:2)')
        output.contains('build.gradle:3)')

        output.count('The DeprecatedPlugin plugin has been deprecated') == 1

        output.count('\tat') > 1
    }
}
