/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.groovy.scripts

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GroovyImportsIntegrationTest extends AbstractIntegrationSpec {

    def "can handle duplicate imports in init scripts"() {
        def initScript = file('init.gradle')
        initScript << """
initscript {
    repositories { ${mavenCentralRepository()} }
    dependencies {
        classpath 'org.apache.commons:commons-math3:3.6.1'
    }
}

import org.apache.commons.math3.util.FastMath
import org.apache.commons.math3.util.FastMath
"""

        expect:
        args("--init-script", initScript.toString())
        succeeds("help")
    }

}
