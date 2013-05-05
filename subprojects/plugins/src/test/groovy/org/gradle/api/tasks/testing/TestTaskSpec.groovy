/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.testing

import org.gradle.api.internal.tasks.testing.TestFramework
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.detection.TestExecuter
import org.gradle.api.internal.tasks.testing.junit.report.TestReporter
import org.gradle.listener.ListenerBroadcast
import org.gradle.util.HelperUtil
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 12/7/12
 */
class TestTaskSpec extends Specification {

    private testExecuter = Mock(TestExecuter)
    private testFramework = Mock(TestFramework)
    private testListenerBroadcaster = Mock(ListenerBroadcast)
    private testOutputListenerBroadcaster = Mock(ListenerBroadcast)

    private task = HelperUtil.createTask(Test, [testExecuter: testExecuter, testFramework: testFramework,
            testListenerBroadcaster: testListenerBroadcaster, testOutputListenerBroadcaster: testOutputListenerBroadcaster])

    public setup(){
        task.setTestReporter(Mock(TestReporter))
        task.setBinResultsDir(task.project.file('build/test-results'))
    }

    def "adds listeners and removes after execution"() {
        when:
        task.executeTests()

        then:
        3 * testListenerBroadcaster.add(_)
        2 * testOutputListenerBroadcaster.add(_)

        then:
        1 * testExecuter.execute(task, _ as TestResultProcessor)

        then:
        1 * testListenerBroadcaster.removeAll({it.size() == 3})
        1 * testOutputListenerBroadcaster.removeAll({it.size() == 2})
    }

    def "removes listeners even if execution fails"() {
        testExecuter.execute(task, _ as TestResultProcessor) >> { throw new RuntimeException("Boo!")}

        when:
        task.executeTests()

        then:
        def ex = thrown(RuntimeException)
        ex.message == "Boo!"

        and:
        1 * testListenerBroadcaster.removeAll({it.size() == 3})
        1 * testOutputListenerBroadcaster.removeAll({it.size() == 2})
    }
}
