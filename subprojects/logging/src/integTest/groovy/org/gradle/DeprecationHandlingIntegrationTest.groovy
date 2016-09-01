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

    // ######################################################################

    def 'DeprecatedPlugin and DeprecatedTask - without full stacktrace.'() {
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
        executer.expectDeprecationWarning()
        run('deprecated', 'broken')

        then:
        output.contains('build.gradle:4)')
        output.contains('build.gradle:6)')
        output.contains('build.gradle:10)')
        !output.contains('(Native Method)')

        and:
        output.count('The DeprecatedPlugin plugin has been deprecated') == 1
        output.count('The someFeature() method has been deprecated') == 1
        output.count('The otherFeature() method has been deprecated') == 1
        output.count('The deprecated task has been deprecated') == 1

        and:
        output.count('\tat') == 3
    }

    // ######################################################################

    def 'DeprecatedPlugin and DeprecatedTask - with full stacktrace.'() {
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
        output.count('\tat') > 3
    }

    // ######################################################################

    def 'DeprecatedPlugin from init script - without full stacktrace.'() {
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

    def 'DeprecatedPlugin from applied script - without full stacktrace.'() {
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

    def 'DeprecatedPlugin from applied script - with full stacktrace.'() {
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
