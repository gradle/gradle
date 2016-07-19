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

import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification

@Requires(TestPrecondition.NOT_WINDOWS)
class YourKitProfilerTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def "Yourkit options should be added to agentpath argument"() {
        given:
        def yjpDir = tmpDir.createDir("yjp")
        def yjpAgentFile = yjpDir.createFile("bin/linux-x86-64/libyjpagent.so")
        def yourkit = new YourKitProfiler("", yjpDir.toString(), OperatingSystem.LINUX)
        def options = [tracing: true]
        when:
        def actualArgs = yourkit.profilerArguments(options)
        then:
        actualArgs == [ "-agentpath:${yjpAgentFile}=tracing".toString() ]
    }

    def "YourKit options should be added to jvm opts"() {
        given:
        def yjpDir = tmpDir.createDir("yjp")
        def yjpAgentFile = yjpDir.createFile("bin/linux-x86-64/libyjpagent.so")
        def yourkit = new YourKitProfiler("", yjpDir.toString(), OperatingSystem.LINUX)
        def options = [tracing: true]
        def invocation = GradleInvocationSpec.builder().
                        distribution(Mock(GradleDistribution)).
                        workingDirectory(tmpDir.getTestDirectory()).
                        useProfiler(yourkit).profilerOpts(options).build()
        expect:
        invocation.jvmOpts == [ "-agentpath:${yjpAgentFile}=tracing".toString() ]
    }

    def "when yourkit properties file doesn't exist, it should use defaults"() {
        given:
        File yourkitPropertiesFile = tmpDir.file("doesnt_exist.properties")
        when:
        def yourkitOptions = YourKitProfiler.loadProperties(yourkitPropertiesFile)
        then:
        yourkitOptions.size() > 0
    }

    def "when yourkit properties file exists, it should be used"() {
        given:
        File yourkitPropertiesFile = tmpDir.file("yourkit.properties")
        yourkitPropertiesFile.text = "sampling=true\ndelay=0"
        when:
        def yourkitOptions = YourKitProfiler.loadProperties(yourkitPropertiesFile)
        then:
        yourkitOptions == [sampling: 'true', delay: '0']
    }
}
