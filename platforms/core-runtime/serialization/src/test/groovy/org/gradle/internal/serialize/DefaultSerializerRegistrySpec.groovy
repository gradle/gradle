/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.serialize

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Timeout
import spock.util.concurrent.AsyncConditions

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DefaultSerializerRegistrySpec extends Specification {
    def numThreads = 10
    List<Class<?>> serializableClasses = [
        Serializable1, Serializable2, Serializable3, Serializable4, Serializable5, Serializable6, Serializable7, Serializable8, Serializable9, Serializable10,
        Serializable11, Serializable12, Serializable13, Serializable14, Serializable15, Serializable16, Serializable17, Serializable18, Serializable19, Serializable20,
        Serializable21, Serializable22, Serializable23, Serializable24, Serializable25, Serializable26, Serializable27, Serializable28, Serializable29, Serializable30,
        Serializable31, Serializable32, Serializable33, Serializable34, Serializable35, Serializable36, Serializable37, Serializable38, Serializable39, Serializable40,
        Serializable41, Serializable42, Serializable43, Serializable44, Serializable45, Serializable46, Serializable47, Serializable48, Serializable49, Serializable50,
        Serializable51, Serializable52, Serializable53, Serializable54, Serializable55, Serializable56, Serializable57, Serializable58, Serializable59, Serializable60,
        Serializable61, Serializable62, Serializable63, Serializable64, Serializable65, Serializable66, Serializable67, Serializable68, Serializable69, Serializable70,
        Serializable71, Serializable72, Serializable73, Serializable74, Serializable75, Serializable76, Serializable77, Serializable78, Serializable79, Serializable80,
        Serializable81, Serializable82, Serializable83, Serializable84, Serializable85, Serializable86, Serializable87, Serializable88, Serializable89, Serializable90,
        Serializable91, Serializable92, Serializable93, Serializable94, Serializable95, Serializable96, Serializable97, Serializable98, Serializable99, Serializable100
    ]

    def latch = new CountDownLatch(1)

    @Subject
    def defaultSerializerRegistry = new DefaultSerializerRegistry()

    @Timeout(15)
    @Issue("https://github.com/JLLeitschuh/ktlint-gradle/issues/518")
    def "invoking DefaultSerializerRegistry from multiple threads"() {
        given:
        def conditions = new AsyncConditions(numThreads)
        def serializer = Stub(Serializer)
        numThreads.times { threadId ->
            Thread.startDaemon("DefaultSerializerRegistry Test #$threadId") {
                conditions.evaluate {
                    serializableClasses.forEach { theClass ->
                        try {
                            defaultSerializerRegistry.useJavaSerialization(theClass)
                            defaultSerializerRegistry.register(theClass, serializer)
                            Thread.sleep(1)
                        } finally {
                            latch.countDown()
                        }
                    }
                }
            }
        }
        when:
        latch.await(5, TimeUnit.SECONDS)
        10_000.times {
            defaultSerializerRegistry.build(SerializableParent)
        }
        then:
        noExceptionThrown()
        conditions.await(5.0)
        where:
        // Rerun this test 10 times
        testIteration << (0..9)
    }

    static class SerializableParent implements Serializable {}

    static class Serializable1 extends SerializableParent {}

    static class Serializable2 extends SerializableParent {}

    static class Serializable3 extends SerializableParent {}

    static class Serializable4 extends SerializableParent {}

    static class Serializable5 extends SerializableParent {}

    static class Serializable6 extends SerializableParent {}

    static class Serializable7 extends SerializableParent {}

    static class Serializable8 extends SerializableParent {}

    static class Serializable9 extends SerializableParent {}

    static class Serializable10 extends SerializableParent {}

    static class Serializable11 extends SerializableParent {}

    static class Serializable12 extends SerializableParent {}

    static class Serializable13 extends SerializableParent {}

    static class Serializable14 extends SerializableParent {}

    static class Serializable15 extends SerializableParent {}

    static class Serializable16 extends SerializableParent {}

    static class Serializable17 extends SerializableParent {}

    static class Serializable18 extends SerializableParent {}

    static class Serializable19 extends SerializableParent {}

    static class Serializable20 extends SerializableParent {}

    static class Serializable21 extends SerializableParent {}

    static class Serializable22 extends SerializableParent {}

    static class Serializable23 extends SerializableParent {}

    static class Serializable24 extends SerializableParent {}

    static class Serializable25 extends SerializableParent {}

    static class Serializable26 extends SerializableParent {}

    static class Serializable27 extends SerializableParent {}

    static class Serializable28 extends SerializableParent {}

    static class Serializable29 extends SerializableParent {}

    static class Serializable30 extends SerializableParent {}

    static class Serializable31 extends SerializableParent {}

    static class Serializable32 extends SerializableParent {}

    static class Serializable33 extends SerializableParent {}

    static class Serializable34 extends SerializableParent {}

    static class Serializable35 extends SerializableParent {}

    static class Serializable36 extends SerializableParent {}

    static class Serializable37 extends SerializableParent {}

    static class Serializable38 extends SerializableParent {}

    static class Serializable39 extends SerializableParent {}

    static class Serializable40 extends SerializableParent {}

    static class Serializable41 extends SerializableParent {}

    static class Serializable42 extends SerializableParent {}

    static class Serializable43 extends SerializableParent {}

    static class Serializable44 extends SerializableParent {}

    static class Serializable45 extends SerializableParent {}

    static class Serializable46 extends SerializableParent {}

    static class Serializable47 extends SerializableParent {}

    static class Serializable48 extends SerializableParent {}

    static class Serializable49 extends SerializableParent {}

    static class Serializable50 extends SerializableParent {}

    static class Serializable51 extends SerializableParent {}

    static class Serializable52 extends SerializableParent {}

    static class Serializable53 extends SerializableParent {}

    static class Serializable54 extends SerializableParent {}

    static class Serializable55 extends SerializableParent {}

    static class Serializable56 extends SerializableParent {}

    static class Serializable57 extends SerializableParent {}

    static class Serializable58 extends SerializableParent {}

    static class Serializable59 extends SerializableParent {}

    static class Serializable60 extends SerializableParent {}

    static class Serializable61 extends SerializableParent {}

    static class Serializable62 extends SerializableParent {}

    static class Serializable63 extends SerializableParent {}

    static class Serializable64 extends SerializableParent {}

    static class Serializable65 extends SerializableParent {}

    static class Serializable66 extends SerializableParent {}

    static class Serializable67 extends SerializableParent {}

    static class Serializable68 extends SerializableParent {}

    static class Serializable69 extends SerializableParent {}

    static class Serializable70 extends SerializableParent {}

    static class Serializable71 extends SerializableParent {}

    static class Serializable72 extends SerializableParent {}

    static class Serializable73 extends SerializableParent {}

    static class Serializable74 extends SerializableParent {}

    static class Serializable75 extends SerializableParent {}

    static class Serializable76 extends SerializableParent {}

    static class Serializable77 extends SerializableParent {}

    static class Serializable78 extends SerializableParent {}

    static class Serializable79 extends SerializableParent {}

    static class Serializable80 extends SerializableParent {}

    static class Serializable81 extends SerializableParent {}

    static class Serializable82 extends SerializableParent {}

    static class Serializable83 extends SerializableParent {}

    static class Serializable84 extends SerializableParent {}

    static class Serializable85 extends SerializableParent {}

    static class Serializable86 extends SerializableParent {}

    static class Serializable87 extends SerializableParent {}

    static class Serializable88 extends SerializableParent {}

    static class Serializable89 extends SerializableParent {}

    static class Serializable90 extends SerializableParent {}

    static class Serializable91 extends SerializableParent {}

    static class Serializable92 extends SerializableParent {}

    static class Serializable93 extends SerializableParent {}

    static class Serializable94 extends SerializableParent {}

    static class Serializable95 extends SerializableParent {}

    static class Serializable96 extends SerializableParent {}

    static class Serializable97 extends SerializableParent {}

    static class Serializable98 extends SerializableParent {}

    static class Serializable99 extends SerializableParent {}

    static class Serializable100 extends SerializableParent {}
}
