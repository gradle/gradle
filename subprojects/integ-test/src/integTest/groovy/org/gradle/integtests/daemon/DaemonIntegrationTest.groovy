/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.integtests.daemon

import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest
import org.junit.Test
import spock.lang.Issue

/**
 * @author: Szczepan Faber, created at: 8/11/11
 */
class DaemonIntegrationTest extends AbstractIntegrationTest {

    @Issue("GRADLE-1249")
    @Test
    void "daemon should use the current working dir"() {
        System.properties['user.dir'] = distribution.testDir.absolutePath

        file('build.gradle') << """
task assertWorkDir << {
    assert new File('').absolutePath == "$distribution.testDir.absolutePath"
}
"""
        //when
        executer.withTasks('assertWorkDir').run()

        //then no exceptions thrown
    }
}