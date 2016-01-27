/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.LeaksFileHandles

@LeaksFileHandles
class WrapperLoggingIntegrationTest extends AbstractIntegrationSpec {

    void setup() {
        assert distribution.binDistribution.exists(): "bin distribution must exist to run this test, you need to run the :distributions:binZip task"
        executer.beforeExecute(new WrapperSetup())
    }

    private prepareWrapper() {
        file("build.gradle") << """
            wrapper {
                distributionUrl = '${distribution.binDistribution.toURI()}'
            }

            task emptyTask
        """
        executer.withTasks('wrapper').run()
        executer.usingExecutable('gradlew').inDirectory(testDirectory)
    }

    def "wrapper does not output anything when executed in quiet mode"() {
        given:
        prepareWrapper()

        when:
        args '-q'
        succeeds("emptyTask")

        then:
        output.empty
    }
}
