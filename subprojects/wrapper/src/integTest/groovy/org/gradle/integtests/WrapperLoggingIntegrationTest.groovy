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

class WrapperLoggingIntegrationTest extends AbstractWrapperIntegrationSpec {
    def "wrapper does not output anything when executed in quiet mode"() {
        given:
        file("build.gradle") << """
task emptyTask
        """
        prepareWrapper()

        when:
        args '-q'
        def result = wrapperExecuter.withTasks("emptyTask").run()

        then:
        result.output.empty
    }
}
