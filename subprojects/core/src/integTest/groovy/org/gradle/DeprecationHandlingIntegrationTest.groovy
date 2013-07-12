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
        output.count("The someFeature() method has been deprecated") == 1
        output.count("The otherFeature() method has been deprecated") == 1
    }
}
