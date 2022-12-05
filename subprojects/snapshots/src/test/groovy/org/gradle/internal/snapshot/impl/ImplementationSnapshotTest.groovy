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

package org.gradle.internal.snapshot.impl

import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashable
import org.gradle.internal.hash.Hasher
import org.gradle.internal.hash.Hashing
import org.gradle.internal.hash.TestHashCodes
import spock.lang.Specification

class ImplementationSnapshotTest extends Specification {

    static final SHARED_UNTRACKABLE_LAMBDA = TestLambdas.createUntrackableLambda()
    static final SHARED_SERIALIZABLE_LAMBDA = TestLambdas.createSerializableLambda()
    static final SHARED_CLASSLOADER_HASH = TestHashCodes.hashCodeFrom(1234)

    def "class name #className is lambda: #lambda"() {
        HashCode classloaderHash = TestHashCodes.hashCodeFrom(1234)

        expect:
        def snapshot = ImplementationSnapshot.of(className, classloaderHash)
        (snapshot instanceof UnknownImplementationSnapshot) == lambda

        where:
        className                                    | lambda
        'SomeClassName'                              | false
        'SomeClassName$1$2$5'                        | false
        'SomeClassName$1$$Lambda$5/23154546436'      | true
        'SomeClassName$$Lambda$154/00000000082FF1F0' | true
        'SomeClassName$$Lambda$267/19410584'         | true
    }

    def "implementation snapshots are not equal for #description"() {
        expect:
        implementationFactory() != implementationFactory()

        where:
        description                                  | implementationFactory
        'unknown classloader for class'              | { -> ImplementationSnapshot.of("SomeClass", null) }
        'unknown classloader for lambda'             | { -> ImplementationSnapshot.of('SomeClass$$Lambda$3/23501234324', null) }
        'unknown lambda'                             | { -> ImplementationSnapshot.of('SomeClass$$Lambda$3/23501234324', SHARED_CLASSLOADER_HASH) }
        'untrackable lambda'                         | { -> ImplementationSnapshot.of('SomeClass$$Lambda$3/23501234324', SHARED_UNTRACKABLE_LAMBDA, SHARED_CLASSLOADER_HASH) }
        'unknown classloader for untrackable lambda' | { -> ImplementationSnapshot.of('SomeClass$$Lambda$3/23501234324', SHARED_UNTRACKABLE_LAMBDA, null) }
    }

    def "cannot hash unknown implementation snapshot"() {
        def snapshot = ImplementationSnapshot.of("SomeClass", null)
        when:
        snapshot.appendToHasher(Mock(Hasher))

        then:
        thrown(UnsupportedOperationException)
        0 * _
    }

    def "implementation snapshots are equal for classes created #description"() {
        expect:
        def snapshot1 = implementationFactory() as ImplementationSnapshot
        def snapshot2 = implementationFactory() as ImplementationSnapshot
        snapshot1 == snapshot2
        hash(snapshot1) == hash(snapshot2)

        where:
        description     | implementationFactory
        'without value' | { -> ImplementationSnapshot.of("SomeClass", SHARED_CLASSLOADER_HASH) }
        'with value'    | { -> ImplementationSnapshot.of("SomeClass", "non lambda value", SHARED_CLASSLOADER_HASH) }
    }

    def "implementation snapshots are equal for #description lambdas"() {
        expect:
        def snapshot1 = implementationFactory() as ImplementationSnapshot
        def snapshot2 = implementationFactory() as ImplementationSnapshot
        snapshot1 == snapshot2
        hash(snapshot1) == hash(snapshot2)

        where:
        description              | implementationFactory
        'same serializable'      | { -> createLambdaSnapshot(SHARED_SERIALIZABLE_LAMBDA) }
        'serializable'           | { -> createLambdaSnapshot(TestLambdas.createSerializableLambda()) }
        'method reference'       | { -> createLambdaSnapshot(TestLambdas.createMethodRefLambda()) }
        'static field capturing' | { -> createLambdaSnapshot(TestLambdas.createClassCapturingLambda()) }
        'instance capturing'     | { -> createLambdaSnapshot(TestLambdas.createInstanceCapturingLambda()) }
    }

    def "implementation snapshots are not equal for lambdas of different functional interfaces"() {
        def actionLambdaSnapshot = createLambdaSnapshot(TestLambdas.createMethodRefLambda())
        def consumerLambdaSnapshot = createLambdaSnapshot(TestLambdas.createSerializableConsumerLambda())

        expect:
        actionLambdaSnapshot != consumerLambdaSnapshot
        hash(actionLambdaSnapshot) != hash(consumerLambdaSnapshot)
    }

    private HashCode hash(Hashable hashable) {
        def hasher = Hashing.newHasher()
        hasher.put(hashable)
        return hasher.hash()
    }

    private ImplementationSnapshot createLambdaSnapshot(lambda) {
        ImplementationSnapshot.of(lambda.getClass().name, lambda, SHARED_CLASSLOADER_HASH)
    }
}
