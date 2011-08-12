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
import org.gradle.util.GUtil
import org.gradle.util.SetSystemProperties
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import spock.lang.Issue

/**
 * @author: Szczepan Faber, created at: 8/11/11
 */
class DaemonIntegrationTest extends AbstractIntegrationTest {

//  Below tests make much more sense when ran against daemon executor (e.g. daemonIntegTest from the console).
//  For development you can uncomment the useDaemon rule temporarily

//    @Rule public final MethodRule useDaemon = new UseDaemon()

    @Rule public final SetSystemProperties systemProperties = new SetSystemProperties()

    @Issue("GRADLE-1249")
    @Test
    void "gradle uses the current working dir"() {
        System.properties['user.dir'] = distribution.testDir.absolutePath

        file('build.gradle') << """
task assertWorkDir << {
    assert new File('').absolutePath == "${distribution.testDir.absolutePath.replace('\\', '\\\\')}"
}
"""
        //when
        executer.withTasks('assertWorkDir').run()

        //then no exceptions thrown
    }

    @Issue("GRADLE-1296")
    @Test
    void "gradle should know the system properties"() {
        file('build.gradle') << """
task assertSysProp << {
    assert System.properties['foo'] == 'bar'
}
"""
        //when
        executer.withArguments("-Dfoo=bar").withTasks('assertSysProp').run()

        //then no exceptions thrown
    }

    @Issue("GRADLE-1296")
    @Test
    @Ignore
    //I don't think it is easily testable with the daemon
    //the problem is that if the daemon process is already alive we cannot alter its env. variables.
    void "gradle should know the env variables"() {
        file('build.gradle') << """
task assertEnv << {
    assert System.getenv('foo') == 'bar'
}
"""
        //when
        executer.reset()
        executer.withEnvironmentVars(GUtil.map("foo", "bar")).withTasks('assertEnv').run()

        //then no exceptions thrown
    }
}
