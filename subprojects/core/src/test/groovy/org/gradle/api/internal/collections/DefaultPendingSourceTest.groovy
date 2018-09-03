/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.collections

class DefaultPendingSourceTest extends AbstractPendingSourceSpec {
    final PendingSource<CharSequence> source = new DefaultPendingSource()//pending

    def "realized pending elements are removed from source"() {
        when:
        source.addPending(provider("provider1"))
        source.addPending(provider("provider2"))
        source.addPending(provider("provider3"))

        then:
        source.size() == 3
        !source.isEmpty()

        when:
        source.realizePending()

        then:
        source.size() == 0
        source.isEmpty()
    }

    def "realized pending elements with a given type are removed from source"() {
        when:
        source.addPending(provider("provider1"))
        source.addPending(provider(new StringBuffer("provider2")))
        source.addPending(provider("provider3"))

        then:
        source.size() == 3
        !source.isEmpty()

        when:
        source.realizePending(String.class)

        then:
        source.size() == 1
        !source.isEmpty()
    }

    def "realized specified pending element are removed from source"() {
        def provider1 = provider("provider1")

        when:
        source.addPending(provider1)
        source.addPending(provider(new StringBuffer("provider2")))
        source.addPending(provider("provider3"))

        then:
        source.size() == 3
        !source.isEmpty()

        when:
        source.realizePending(provider1)

        then:
        source.size() == 2
        !source.isEmpty()
    }

    def "cannot realize pending elements when realize action is not set"() {
        given:
        source.onRealize(null)

        when:
        source.addPending(provider("provider1"))
        source.addPending(provider("provider2"))
        source.addPending(provider("provider3"))
        source.realizePending()

        then:
        thrown(IllegalStateException)
    }
}
