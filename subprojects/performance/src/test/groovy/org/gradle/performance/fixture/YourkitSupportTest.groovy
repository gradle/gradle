/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.performance.fixture

import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification


class YourkitSupportTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def "Yourkit options should be passed to executer"() {
        given:
        def yjpDir = tmpDir.createDir("yjp")
        def yjpAgentFile = yjpDir.createFile("bin/linux-x86-64/libyjpagent.so")
        def yourkit = new YourkitSupport("", yjpDir.toString(), OperatingSystem.LINUX)
        def executer = Mock(GradleExecuter)
        def options = [tracing: true]
        when:
        yourkit.enableYourkit(executer, options)
        then:
        1 * executer.withBuildJvmOpts({ it == "-agentpath:${yjpAgentFile}=tracing".toString() })


    }

    def "when yourkit properties file doesn't exist, it should use defaults"() {
        given:
        File yourkitPropertiesFile = tmpDir.file("doesnt_exist.properties")
        when:
        def yourkitOptions = YourkitSupport.loadProperties(yourkitPropertiesFile)
        then:
        yourkitOptions.size() > 0
    }

    def "when yourkit properties file exists, it should be used"() {
        given:
        File yourkitPropertiesFile = tmpDir.file("yourkit.properties")
        yourkitPropertiesFile.text = "sampling=true\ndelay=0"
        when:
        def yourkitOptions = YourkitSupport.loadProperties(yourkitPropertiesFile)
        then:
        yourkitOptions == [sampling: 'true', delay: '0']
    }
}
