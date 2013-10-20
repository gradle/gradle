/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.execution.taskgraph

import org.gradle.api.Task
import org.gradle.api.internal.TaskInternal
import spock.lang.Specification

/**
 * Created with IntelliJ IDEA.
 * User: mark.koops
 * Date: 20-10-13
 * Time: 14:12
 * To change this template use File | Settings | File Templates.
 */
class MutexManagerTest extends Specification {
    def MutexManager manager;

    def setup() {
        manager = new MutexManager();
    }

    def "should accept to claim a mutex"() {
        def mutex = "mutex"

        given:
        Task a = task(mutex)

        expect:
        manager.claim(a)
    }

    def "should accept to claim a null mutex"() {
        def nullMutex = null

        given:
        Task a = task(nullMutex)

        expect:
        manager.claim(a)
    }

    def "should reject to claim a mutex twice"() {
        def mutex = "mutex"

        given:
        Task a = task(mutex)
        manager.claim(a)
        Task b = task(mutex)

        expect:
        !manager.claim(b)
    }

    def "should accept to claim a null mutex twice"() {
        def nullMutex = null

        given:
        Task a = task(nullMutex)
        manager.claim(a)
        Task b = task(nullMutex)

        expect:
        manager.claim(b)
    }

    def "should accept to release a claimed mutex"() {
        def mutex = "mutex"

        when:
        Task a = task(mutex)
        manager.claim(a)

        then:
        manager.release(a)
    }

    def "should accept to release a null mutex"() {
        def nullMutex = null

        when:
        Task a = task(nullMutex)
        manager.claim(a)

        then:
        manager.release(a)
    }

    private TaskInternal task(final String mutex) {
        TaskInternal task = Mock()
        task.getMutex() >> (mutex)
        return task
    }
}
