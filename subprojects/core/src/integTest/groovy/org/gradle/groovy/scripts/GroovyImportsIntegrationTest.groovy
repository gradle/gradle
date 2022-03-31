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
import spock.lang.IgnoreRest

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

    @IgnoreRest
    def "can compile basic script"() {
        buildScript """
           println 'hello'
           @groovy.transform.CompileStatic
def myMethod(Task task) {
    task.doNotTrackState("dont")
}
        """

        expect:
        succeeds("help"
            , "-Dorg.gradle.api.version=6.9.1", "-Dgradle.api.repository.url=file:///Users/wolfs/projects/gradle/provider-api-migration-testbed/gradle-repository/build/repo"
        )
    }
}
