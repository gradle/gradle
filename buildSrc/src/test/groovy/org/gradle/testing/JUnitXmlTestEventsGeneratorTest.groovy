/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testing

import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.internal.event.ListenerBroadcast
import spock.lang.Specification

class JUnitXmlTestEventsGeneratorTest extends Specification {
    def "uses correct failure message"() {
        def xml = """
            <testsuite name="org.gradle.performance.GradleScriptKotlinBuildPerformanceTest" tests="4" skipped="3" failures="1" errors="0" timestamp="2016-12-16T10:18:55" hostname="ubuntu47.buildfarm.gradle.org" time="1628.799">
                <properties/>
                <testcase name="configuration of ktsSmall" classname="org.gradle.performance.GradleScriptKotlinBuildPerformanceTest" time="0.001">
                    <skipped/>
                </testcase>
                <testcase name="configuration of ktsSmall (--recompile-scripts)" classname="org.gradle.performance.GradleScriptKotlinBuildPerformanceTest" time="0.0">
                    <skipped/>
                </testcase>
                <testcase name="configuration of ktsManyProjects" classname="org.gradle.performance.GradleScriptKotlinBuildPerformanceTest" time="0.001">
                    <skipped/>
                </testcase>
                <testcase name="configuration of ktsManyProjects (--recompile-scripts)" classname="org.gradle.performance.GradleScriptKotlinBuildPerformanceTest" time="1628.796">
                    <failure message="java.lang.AssertionError: Speed Results for test project 'ktsManyProjects' with tasks help: we're slower than 3.4-20161216000013+0000.&#10;Difference: 484 ms slower (484 ms), 2.51%, max regression: 116.443 ms&#10;  Current Gradle median: 19.778 s min: 19.684 s, max: 20.011 s, se: 86.89 ms, sem: 19.429 ms&#10;  &gt; [19.901 s, 19.792 s, 19.705 s, 20.011 s, 19.751 s, 19.756 s, 19.89 s, 19.877 s, 19.726 s, 19.684 s, 19.796 s, 19.764 s, 19.709 s, 19.833 s, 19.709 s, 19.822 s, 19.871 s, 19.694 s, 19.704 s, 19.876 s]&#10;  Gradle 3.4-20161216000013+0000 median: 19.294 s min: 19.159 s, max: 19.445 s, se: 79.75 ms, sem: 17.833 ms&#10;  &gt; [19.425 s, 19.275 s, 19.334 s, 19.402 s, 19.285 s, 19.303 s, 19.415 s, 19.309 s, 19.272 s, 19.445 s, 19.236 s, 19.28 s, 19.396 s, 19.159 s, 19.213 s, 19.262 s, 19.38 s, 19.225 s, 19.22 s, 19.372 s]&#10;&#10;" type="java.lang.AssertionError">java.lang.AssertionError: Speed Results for test project 'ktsManyProjects' with tasks help: we're slower than 3.4-20161216000013+0000.
                Difference: 484 ms slower (484 ms), 2.51%, max regression: 116.443 ms
                  Current Gradle median: 19.778 s min: 19.684 s, max: 20.011 s, se: 86.89 ms, sem: 19.429 ms
                  &gt; [19.901 s, 19.792 s, 19.705 s, 20.011 s, 19.751 s, 19.756 s, 19.89 s, 19.877 s, 19.726 s, 19.684 s, 19.796 s, 19.764 s, 19.709 s, 19.833 s, 19.709 s, 19.822 s, 19.871 s, 19.694 s, 19.704 s, 19.876 s]
                  Gradle 3.4-20161216000013+0000 median: 19.294 s min: 19.159 s, max: 19.445 s, se: 79.75 ms, sem: 17.833 ms
                  &gt; [19.425 s, 19.275 s, 19.334 s, 19.402 s, 19.285 s, 19.303 s, 19.415 s, 19.309 s, 19.272 s, 19.445 s, 19.236 s, 19.28 s, 19.396 s, 19.159 s, 19.213 s, 19.262 s, 19.38 s, 19.225 s, 19.22 s, 19.372 s]
                
                
                    at org.gradle.performance.results.CrossVersionPerformanceResults.throwAssertionErrorIfNotFlaky(CrossVersionPerformanceResults.groovy:101)
                    at org.gradle.performance.results.CrossVersionPerformanceResults.assertCurrentVersionHasNotRegressed(CrossVersionPerformanceResults.groovy:93)
                    at org.gradle.performance.fixture.CrossVersionPerformanceTestRunner.run(CrossVersionPerformanceTestRunner.groovy:121)
                    at org.gradle.performance.GradleScriptKotlinBuildPerformanceTest.build(GradleScriptKotlinBuildPerformanceTest.groovy:37)
                </failure>
                </testcase>
            </testsuite>""".stripIndent()
        def gpath = new XmlSlurper().parseText(xml)
        ListenerBroadcast<TestListener> testListenerBroadcast = Mock()
        TestListener testListener = Mock()
        ListenerBroadcast<TestOutputListener> testOutputListenerBroadcast = Mock()
        TestOutputListener testOutputListener = Mock()
        TestDescriptorInternal testDescriptor = null
        TestResult testResult = null
        Object build = new XmlSlurper().parseText("""<build webUrl="http://some.url"><properties><property name="scenario" value="configuration of ktsManyProjects (--recompile-scripts)" inherited="false"/></properties></build>""")

        when:
        new JUnitXmlTestEventsGenerator(testListenerBroadcast, testOutputListenerBroadcast).processXml(gpath, build)

        then:
        _ * testListenerBroadcast.getSource() >> testListener
        _ * testOutputListenerBroadcast.getSource() >> testOutputListener
        1 * testListener.afterTest(_, _) >> { arguments ->
            testDescriptor = arguments[0]
            testResult = arguments[1]
        }

        testDescriptor.name == 'configuration of ktsManyProjects (--recompile-scripts)'
        testDescriptor.className == 'org.gradle.performance.GradleScriptKotlinBuildPerformanceTest'

        testResult.resultType == TestResult.ResultType.FAILURE
        testResult.exception.toString() startsWith """java.lang.AssertionError: Speed Results for test project 'ktsManyProjects' with tasks help: we're slower than 3.4-20161216000013+0000."""
    }
}
