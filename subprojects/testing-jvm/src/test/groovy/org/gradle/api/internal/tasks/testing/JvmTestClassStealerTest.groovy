/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.tasks.testing

import org.gradle.api.internal.tasks.testing.worker.ForkingTestClassProcessor
import spock.lang.Specification

class JvmTestClassStealerTest extends Specification {
    JvmTestClassStealer instant = new JvmTestClassStealer()
    TestClassRunInfo findTest = Mock(TestClassRunInfo)
    TestClassRunInfo workingTest = Mock(TestClassRunInfo)
    TestClassRunInfo stolenTest = Mock(TestClassRunInfo)


    def registerFindAndStealTest(){
        given:
        ForkingTestClassProcessor remote = Mock(ForkingTestClassProcessor) {
            1 * handOver(workingTest) >> { args -> instant.handOverTestClass it, workingTest, false }
            1 * handOver(stolenTest) >> { args -> instant.handOverTestClass it, stolenTest, true }
        }

        when:
        instant.add findTest, remote
        instant.add workingTest, remote
        instant.add stolenTest, remote
        instant.remove findTest

        then:
        instant.trySteal() === stolenTest
        instant.trySteal() === null

        0 * remote.handOver(findTest)
    }

    def registerAndStopWorker(){
        given:
        ForkingTestClassProcessor remote = Mock(ForkingTestClassProcessor)

        when:
        instant.add findTest, remote
        instant.stopped remote

        then:
        instant.trySteal() === null
    }

    def registerAndStopWorkerWhileSteal(){
        given:
        ForkingTestClassProcessor remote = Mock(ForkingTestClassProcessor) {
            handOver(findTest) >> { args ->  instant.stopped it}
        }

        when:
        instant.add findTest, remote

        then:
        instant.trySteal() === null
    }
}
