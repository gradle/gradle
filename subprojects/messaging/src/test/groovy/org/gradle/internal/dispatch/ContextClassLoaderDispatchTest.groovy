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


package org.gradle.internal.dispatch

import spock.lang.Specification

class ContextClassLoaderDispatchTest extends Specification {
    private final Dispatch<String> target = Mock()
    private final ClassLoader appClassLoader = new ClassLoader() {}
    private ClassLoader original
    private final ContextClassLoaderDispatch dispatch = new ContextClassLoaderDispatch(target, appClassLoader)

    def setup() {
        original = Thread.currentThread().contextClassLoader
    }

    public void cleanup() {
        Thread.currentThread().contextClassLoader = original
    }

    def 'sets ContextClassLoader during dispatch'() {
        when:
        dispatch.dispatch('message')

        then:
        1 * target.dispatch('message') >> {
            assertContextClassloaderIs(appClassLoader)
        }
        0 * _._

        assertContextClassloaderIs(original)
    }

    def 'cleans up after failure'() {
        given:
        def failure = new RuntimeException()

        when:
        dispatch.dispatch('message')

        then:
        1 * target.dispatch('message') >> {
            assertContextClassloaderIs(appClassLoader)
            throw failure
        }
        0 * _._
        RuntimeException e = thrown()
        e.is(failure)
        assertContextClassloaderIs(original)
    }

    private static void assertContextClassloaderIs(ClassLoader expectedClassloader) {
        assert Thread.currentThread().contextClassLoader.is(expectedClassloader)
    }
}
