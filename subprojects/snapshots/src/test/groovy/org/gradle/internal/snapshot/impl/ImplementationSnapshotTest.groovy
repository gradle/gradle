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
import org.gradle.internal.hash.TestHashCodes
import spock.lang.Specification

class ImplementationSnapshotTest extends Specification {

    def "class name #className is lambda: #lambda"() {
        HashCode classloaderHash = TestHashCodes.hashCodeFrom(1234)

        expect:
        ImplementationSnapshot.of(className, classloaderHash).unknown == lambda

        where:
        className                                    | lambda
        'SomeClassName'                              | false
        'SomeClassName$1$2$5'                        | false
        'SomeClassName$1$$Lambda$5/23154546436'      | true
        'SomeClassName$$Lambda$154/00000000082FF1F0' | true
        'SomeClassName$$Lambda$267/19410584'         | true
    }

    def "implementation snapshots are not equal when unknown"() {
        HashCode classloaderHash = TestHashCodes.hashCodeFrom(1234)
        String lambdaClassName = 'SomeClass$$Lambda$3/23501234324'
        def className = "SomeClass"

        expect:
        ImplementationSnapshot.of(className, classloaderHash) == ImplementationSnapshot.of(className, classloaderHash)
        ImplementationSnapshot.of(className, null) != ImplementationSnapshot.of(className, null)
        ImplementationSnapshot.of(lambdaClassName, classloaderHash) != ImplementationSnapshot.of(lambdaClassName, classloaderHash)
        ImplementationSnapshot.of(lambdaClassName, null) != ImplementationSnapshot.of(lambdaClassName, null)
    }
}
