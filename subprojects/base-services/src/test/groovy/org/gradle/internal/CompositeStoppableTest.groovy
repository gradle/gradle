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

package org.gradle.internal

import spock.lang.Specification

import static org.gradle.internal.CompositeStoppable.closeable

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

    def callsACloseMethodOnArbitraryObject() {
        ClassWithCloseMethod a = Mock()

        stoppable.add(a)

        when:
        stoppable.stop()

        then:
        1 * a.close()
    }

    def callsAStopMethodOnArbitraryObject() {
        ClassWithStopMethod a = Mock()

        stoppable.add(a)

        when:
        stoppable.stop()

        then:
        1 * a.stop()
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

    def "can close closeables"() {
        def a = Mock(Closeable)
        def b = Mock(Closeable)

        def closeable = closeable(a, b)

        when:
        closeable.close()

        then:
        1 * a.close()
        1 * b.close()
        0 * _._
    }

    def "closing propagates IOException"() {
        def a = Mock(Closeable)
        def b = Mock(Closeable)
        def c = Mock(Closeable)

        when:
        closeable(a, b, c).close()

        then:
        def ex = thrown(IOException)
        ex.message == 'Boo!'
        1 * a.close() >> { throw new IOException("Boo!")}
        1 * b.close() >> { throw new RuntimeException("Foo!")}
        1 * c.close()
        0 * _._
    }

    def "closing propagates RuntimeException"() {
        def a = Mock(Closeable)
        def b = Mock(Closeable)

        when:
        closeable(a, b).close()

        then:
        def ex = thrown(RuntimeException)
        ex.message == 'Foo!'

        1 * a.close() >> { throw new RuntimeException("Foo!")}
        1 * b.close() >> { throw new IOException("Boo!")}
        0 * _._
    }

    static class ClassWithCloseMethod {
        void close() {
        }
    }

    static class ClassWithStopMethod {
        void stop() {
        }
    }
}
