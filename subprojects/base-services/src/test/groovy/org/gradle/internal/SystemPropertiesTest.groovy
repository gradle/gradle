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

package org.gradle.internal

import spock.lang.Specification

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

class SystemPropertiesTest extends Specification {
    def "can be queried for standard system properties"() {
        expect:
        SystemProperties.instance.standardProperties.contains("os.name")
        !SystemProperties.instance.standardProperties.contains("foo.bar")
    }

    def "prohibits concurrent reads of Java Home while factory is busy"() {
        def repeat = 100
        def initialJavaHomePath = SystemProperties.instance.javaHomeDir.path
        def temporaryJavaHomePath = initialJavaHomePath + "-2"

        def valuesSeenByFactory = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>())

        def factory = new Factory<Object>() {
            @Override
            Object create() {
                valuesSeenByFactory.add(SystemProperties.instance.javaHomeDir.path)
                return new Object()
            }
        }

        def latch = new CountDownLatch(2)

        new Thread(new Runnable() {
            @Override
            void run() {
                for (int i = 0; i < repeat; i++) {
                    SystemProperties.instance.withJavaHome(new File(temporaryJavaHomePath), factory)
                }
                latch.countDown()
            }
        }).start()

        def valuesSeenByAnotherThread = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>())

        new Thread(new Runnable() {
            @Override
            void run() {
                for (int i = 0; i < repeat; i++) {
                    valuesSeenByAnotherThread.add(SystemProperties.instance.javaHomeDir.path)
                }

                latch.countDown()
            }
        }).start()

        latch.await()

        expect:
        valuesSeenByFactory == Collections.singleton(temporaryJavaHomePath)
        valuesSeenByAnotherThread == Collections.singleton(initialJavaHomePath)
    }
}
