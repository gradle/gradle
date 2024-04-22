/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.invocation

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GradleLifecycleIsolationIntegrationTest extends AbstractIntegrationSpec {

    def 'lifecycle actions are isolated per project and their order is preserved'() {
        given:
        settingsFile '''
            rootProject.name = 'root'
            include 'sub'

            def log = []
            gradle.lifecycle.beforeProject { p ->
                log << "1: before $p.name $p.version"
                p.version = 'from action'
            }
            gradle.lifecycle.beforeProject { p ->
                log << "2: before $p.name $p.version"
            }
            gradle.lifecycle.afterProject { p ->
                log << "1: after $p.name $p.version"
            }
            gradle.lifecycle.afterProject { p ->
                log << "2: after $p.name $p.version"
            }
            gradle.lifecycle.afterProject {
                println log
            }
        '''

        def script = '''
            println "$name with version $version"
            version = 'from script'
        '''
        buildFile script
        groovyFile 'sub/build.gradle', script

        when:
        succeeds 'help'

        then:
        outputContains 'root with version from action'
        outputContains 'sub with version from action'
        outputContains '[1: before root unspecified, 2: before root from action, 1: after root from script, 2: after root from script]'
        outputContains '[1: before sub unspecified, 2: before sub from action, 1: after sub from script, 2: after sub from script]'
    }
}
