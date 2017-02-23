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

import org.gradle.api.internal.provider.AbstractProvider
import org.gradle.api.internal.tasks.TaskResolver
import spock.lang.Specification

class ProviderTest extends Specification {

    def taskResolver = Mock(TaskResolver)

    def "can compare with other instance returning value #value"() {
        given:
        Provider<Boolean> provider1 = new BooleanProvider(taskResolver, true)
        Provider<Boolean> provider2 = new BooleanProvider(taskResolver, value)

        expect:
        (provider1 == provider2) == equality
        (provider1.hashCode() == provider2.hashCode()) == hashCode
        (provider1.toString() == provider2.toString()) == stringRepresentation

        where:
        value | equality | hashCode | stringRepresentation
        true  | true     | true     | true
        false | false    | false    | false
        null  | false    | false    | false
    }

    static class BooleanProvider extends AbstractProvider<Boolean> {

        private final Boolean value

        BooleanProvider(TaskResolver taskResolver, Boolean value) {
            super(taskResolver)
            this.value = value
        }

        Boolean get() {
            value
        }
    }
}
