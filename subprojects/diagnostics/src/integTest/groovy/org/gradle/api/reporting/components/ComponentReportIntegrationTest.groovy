/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.reporting.components

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ComponentReportIntegrationTest extends AbstractIntegrationSpec {
    def "informs the user when project has no components defined"() {
        when:
        succeeds "components"

        then:
        output.contains("No components.")
    }

    def "shows details of Java library"() {
        given:
        buildFile << """
plugins {
    id 'jvm-component'
    id 'java-lang'
}

jvm {
    libraries {
        someLib
    }
}
"""
        when:
        succeeds "components"

        then:
        output.contains("JVM library 'someLib'")
    }

    def "shows details of native C++ library"() {
        given:
        buildFile << """
plugins {
    id 'cpp'
}

nativeRuntime {
    libraries {
        someLib
    }
}
"""
        when:
        succeeds "components"

        then:
        output.contains("library 'someLib'")
    }

    def "shows details of native C++ executable"() {
        given:
        buildFile << """
plugins {
    id 'cpp'
}

nativeRuntime {
    executables {
        someExe
    }
}
"""
        when:
        succeeds "components"

        then:
        output.contains("executable 'someExe'")
    }
}
