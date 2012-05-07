/*
 * Copyright 2012 the original author or authors.
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



package org.gradle.process.internal

import spock.lang.Specification
import spock.lang.Timeout

/**
 * by Szczepan Faber, created at: 4/29/12
 */
@Timeout(20)
class TimeKeepingExecuterTest extends Specification {

    def executer = new TimeKeepingExecuter()
    Boolean timeouted
    final lock = new Object()

    def "timeout hits before operation ends"() {
        when:
        executer.execute(longOperation(10000), markTimeouted(), 1000, "foo")
        then:
        timeouted
    }

    def "timeout hits before operation starts"() {
        when:
        executer.execute(longOperation(10000), markTimeouted(), 1, "foo")
        then:
        timeouted
    }

    def "operation ends before timeout"() {
        expect:
        timeouted == null
        when:
        executer.execute(longOperation(1), markTimeouted(), 10000, "foo")
        then:
        timeouted != null
        !timeouted
    }

    def "operation fails before timeout"() {
        when:
        executer.execute({ throw new RuntimeException("Boo!")} as Runnable, markTimeouted(), 10000, "foo")
        then:
        def ex = thrown(RuntimeException)
        ex.message == "Boo!"
    }

    Runnable markTimeouted() {
        return {
            timeouted = true
            synchronized (lock) {
                lock.notifyAll()
            }
        } as Runnable
    }

    Runnable longOperation(int millis) {
        return {
            synchronized (lock) {
                timeouted = false
                lock.wait(millis)
            }
        } as Runnable
    }
}
