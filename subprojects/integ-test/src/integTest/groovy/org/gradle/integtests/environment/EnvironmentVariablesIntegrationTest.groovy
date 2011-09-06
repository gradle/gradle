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

package org.gradle.integtests.environment

import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest
import org.gradle.launcher.env.LenientEnvHacker
import org.junit.Test
import spock.lang.Issue

/**
 * @author: Szczepan Faber, created at: 8/11/11
 */
class EnvironmentVariablesIntegrationTest extends AbstractIntegrationTest {

    @Issue("GRADLE-1762")
    @Test
    void "gradle should detect changes to environment variables"() {
        file('build.gradle') << """
task printEnv << {
    println System.getenv('foo')
}
"""
        //it is useful to test it with the daemon
//        executer.type = Executer.daemon

        //when
        new LenientEnvHacker().setenv("foo", "gradle rocks!")

        //then
        def out = executer.withTasks('printEnv').withArguments('-s').run().output
        assert out.contains("gradle rocks!")

        //when
        new LenientEnvHacker().setenv("foo", "and will be even better")

        //then
        out = executer.withTasks('printEnv').run().output
        assert out.contains("and will be even better")
    }

    //TODO SF add similar coverage for the system properties
}
