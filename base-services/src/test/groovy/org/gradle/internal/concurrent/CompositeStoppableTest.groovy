/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.internal.concurrent

import spock.lang.Specification

class CompositeStoppableTest extends Specification {
    private final CompositeStoppable stoppable = new CompositeStoppable()

    def stopsAllElementsOnStop() {
        Stoppable a = Mock()
        Stoppable b = Mock()
        stoppable.add(a)
        stoppable.add(b)

        when:
        stoppable.stop()

        then:
        1 * a.stop()
        1 * b.stop()
    }

    def stopsAllElementsWhenOneFailsToStop() {
        Stoppable a = Mock()
        Stoppable b = Mock()
        RuntimeException failure = new RuntimeException()
        stoppable.add(a)
        stoppable.add(b)

        when:
        stoppable.stop()

        then:
        1 * a.stop() >> { throw failure }
        1 * b.stop()
        def e = thrown(RuntimeException)
        e == failure
    }

    def stopsAllElementsWhenMultipleFailToStop() {
        Stoppable a = Mock()
        Stoppable b = Mock()
        RuntimeException failure1 = new RuntimeException()
        RuntimeException failure2 = new RuntimeException()
        stoppable.add(a)
        stoppable.add(b)

        when:
        stoppable.stop()

        then:
        1 * a.stop() >> { throw failure1 }
        1 * b.stop() >> { throw failure2 }
        def e = thrown(RuntimeException)
        e == failure1
    }

    def closesACloseableElement() {
        Closeable a = Mock()
        Stoppable b = Mock()

        stoppable.add(a)
        stoppable.add(b)

        when:
        stoppable.stop()

        then:
        1 * a.close()
        1 * b.stop()
    }

    def doesNothingWhenArbitraryObjectDoesNotHaveStopOrCloseMethod() {
        Runnable r = Mock()

        stoppable.add(r)

        when:
        stoppable.stop()

        then:
        0 * r._
    }

    def ignoresNullElements() {
        Stoppable a = Mock()
        stoppable.add([null])
        stoppable.add(a)

        when:
        stoppable.stop()

        then:
        1 * a.stop()
    }
}
