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

package org.gradle.launcher.continuous

import org.gradle.internal.concurrent.StoppableExecutor
import spock.lang.Specification


class DefaultTriggerGeneratorTest extends Specification {
    def executor = Mock(StoppableExecutor)
    def triggerStrategy = Mock(Runnable)
    def triggerGenerator = new DefaultTriggerGenerator(executor, triggerStrategy)

    def "start executes the trigger strategy"() {
        when:
        triggerGenerator.start()
        then:
        1 * executor.execute(triggerStrategy)
    }

    def "stop stops the executor"() {
        when:
        triggerGenerator.stop()
        then:
        1 * executor.stop()
    }
}
