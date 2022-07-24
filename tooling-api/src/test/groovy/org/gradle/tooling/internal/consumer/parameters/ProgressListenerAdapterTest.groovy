/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.consumer.parameters

import org.gradle.tooling.ProgressListener
import spock.lang.Specification

class ProgressListenerAdapterTest extends Specification {
    final ProgressListener listener = Mock()
    ProgressListenerAdapter adapter = new ProgressListenerAdapter(Collections.singletonList(listener))

    def notifiesListenerOnOperationStartAndEnd() {
        when:
        adapter.onOperationStart('main')

        then:
        1 * listener.statusChanged({it.description == 'main'})

        when:
        adapter.onOperationEnd()

        then:
        1 * listener.statusChanged({it.description == ''})
        0 * _._
    }

    def notifiesListenerOnNestedOperationStartAndEnd() {
        when:
        adapter.onOperationStart('main')
        adapter.onOperationStart('nested')

        then:
        1 * listener.statusChanged({it.description == 'main'})
        1 * listener.statusChanged({it.description == 'nested'})

        when:
        adapter.onOperationEnd()
        adapter.onOperationEnd()

        then:
        1 * listener.statusChanged({it.description == 'main'})
        1 * listener.statusChanged({it.description == ''})
        0 * _._
    }
}
