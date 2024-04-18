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
                log << "1: before $p.name with version $p.version"
            }
            gradle.lifecycle.beforeProject { p ->
                log << "2: before $p.name with version $p.version"
            }
            gradle.lifecycle.beforeProject {
                print log
            }
        '''
        buildFile '''
            version = '1.0'
        '''
        groovyFile 'sub/build.gradle', '''
            version = '2.0'
        '''

        when:
        succeeds 'help'

        then:
        outputContains '[1: before root with version unspecified, 2: before root with version unspecified]'
        outputContains '[1: before sub with version unspecified, 2: before sub with version unspecified]'
    }
}
