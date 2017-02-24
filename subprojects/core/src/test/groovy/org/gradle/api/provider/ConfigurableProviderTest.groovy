/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.provider

import org.gradle.api.Task
import org.gradle.api.internal.provider.AbstractConfigurableProvider
import org.gradle.api.internal.tasks.TaskResolver
import spock.lang.Specification

class ConfigurableProviderTest extends Specification {

    def taskResolver = Mock(TaskResolver)
    def task1 = Mock(Task)
    def task2 = Mock(Task)

    def "can define build dependencies"() {
        given:
        ConfigurableProvider<Boolean> provider = new BooleanProvider(taskResolver)

        expect:
        provider
        provider.buildDependencies
        provider.builtBy.empty

        when:
        provider.builtBy(task1)

        then:
        provider.builtBy.size() == 1
        provider.builtBy == [task1] as Set

        when:
        provider.builtBy(task1, task2)

        then:
        provider.builtBy.size() == 2
        provider.builtBy == [task1, task2] as Set
    }

    static class BooleanProvider extends AbstractConfigurableProvider<Boolean> {

        BooleanProvider(TaskResolver taskResolver) {
            super(taskResolver)
        }

        Boolean get() {
            true
        }
    }
}
