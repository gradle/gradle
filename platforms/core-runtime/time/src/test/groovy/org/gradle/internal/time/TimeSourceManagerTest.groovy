/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.time

import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.OsTestPreconditions
import spock.lang.Specification

class TimeSourceManagerTest extends Specification {

    def "disabled manager is not active"() {
        given:
        def manager = TimeSourceManager.start(false)

        expect:
        !manager.tickerActive

        cleanup:
        manager.close()
    }

    def "ticker advances the cached time and readings never decrease"() {
        given:
        def manager = TimeSourceManager.startWithoutProbe(1)
        def timeSource = manager.cachedTimeSource

        expect:
        ConcurrentTestUtil.poll {
            assert manager.tickerActive
        }

        when:
        def first = timeSource.nanoTime()

        then:
        ConcurrentTestUtil.poll {
            assert timeSource.nanoTime() > first
        }

        and:
        def previous = timeSource.nanoTime()
        (1..1000).every {
            def current = timeSource.nanoTime()
            def ok = current >= previous
            previous = current
            ok
        }

        cleanup:
        manager.close()
    }

    @Requires(OsTestPreconditions.NotWindows)
    def "probing manager does not block and stays inactive when the timer is fast"() {
        given:
        // This test machine is assumed to have a healthy timer. We disable this test on windows
        // since our Windows CI machines are known to have slow timers.
        def manager = TimeSourceManager.start(true)

        expect:
        ConcurrentTestUtil.poll {
            assert !manager.tickerActive
        }

        cleanup:
        manager.close()
    }

    def "close stops the ticker"() {
        given:
        def manager = TimeSourceManager.startWithoutProbe(1)
        ConcurrentTestUtil.poll {
            assert manager.tickerActive
        }

        when:
        manager.close()

        then:
        !manager.tickerActive

        and:
        // Readings still work after close, they just no longer advance with the ticker
        manager.cachedTimeSource.nanoTime() > 0

        when:
        manager.close()

        then:
        noExceptionThrown()
    }

    @Requires(OsTestPreconditions.Windows)
    def "activating the ticker installs the source into Time and stopping restores direct readings"() {
        when:
        def manager = TimeSourceManager.startWithoutProbeAndInstall(1)

        then:
        ConcurrentTestUtil.poll {
            assert Time.getEffectiveTimeSource().is(manager.cachedTimeSource)
        }

        and:
        // The clock still works and remains monotonic while served by the cached time source
        def t1 = Time.clock().getCurrentTime()
        def t2 = Time.clock().getCurrentTime()
        t2 >= t1

        when:
        manager.close()

        then:
        ConcurrentTestUtil.poll {
            assert !Time.getEffectiveTimeSource().is(manager.cachedTimeSource)
        }

        cleanup:
        manager.close()
    }

    @Requires(OsTestPreconditions.NotWindows)
    def "the time source cannot be swapped on platforms with a cheap timer"() {
        when:
        Time.installTimeSource(new TimeSourceManager.CachedTimeSource())

        then:
        thrown(IllegalStateException)
    }

}
