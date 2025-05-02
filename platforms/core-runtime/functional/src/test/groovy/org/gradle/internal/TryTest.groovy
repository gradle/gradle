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

package org.gradle.internal

import spock.lang.Specification

class TryTest extends Specification {

    def "successful is successful"() {
        expect:
        Try.successful(10).successful
    }

    def "failure is mot successful"() {
        expect:
        !Try.failure(new RuntimeException()).successful
    }

    def "converts failing callable"() {
        def failure = new Exception("Failure")
        def runtimeFailure = new RuntimeException("Runtime exception")

        expect:
        Try.ofFailable { throw failure } == Try.failure(failure)
        Try.ofFailable { throw runtimeFailure } == Try.failure(runtimeFailure)
    }

    def "captures result"() {
        expect:
        Try.ofFailable { result } == Try.successful(result)

        where:
        result << [['a', 'b', 'c'], "Some string", 5]
    }

    def "successful (flat) map"() {
        expect:
        Try.successful(10).flatMap { it -> Try.successful(it + 2) } == Try.successful(12)
        Try.successful(20).map { (it + 10).toString() } == Try.successful('30')
        Try.successful(20).tryMap { (it + 10).toString() } == Try.successful('30')
    }

    def "flat map failure"() {
        def initial = Try.<Integer>failure(new RuntimeException("failed"))
        expect:
        initial.flatMap { it.toString() } == initial
    }

    def "failing flat map"() {
        def failure = new RuntimeException("failed")
        when:
        Try.successful(10).flatMap { throw failure }
        then:
        def ex = thrown Exception
        ex == failure
    }

    def "map a failure"() {
        def failure = Try.<Integer>failure(new RuntimeException("failed"))
        expect:
        failure.map { it + 1 } == failure
    }

    def "fail during tryMap"() {
        def failure = new RuntimeException("failure")
        expect:
        Try.successful(10).tryMap { throw failure } == Try.failure(failure)
    }

    def "fail during map"() {
        def failure = new RuntimeException("failure")
        when:
        Try.successful(10).map { throw failure }
        then:
        def ex = thrown Exception
        ex == failure
    }

    def "map failure"() {
        Exception finalFailure = new Exception("final")
        expect:
        Try.successful(10).mapFailure { finalFailure } == Try.successful(10)
        Try.failure(new RuntimeException("other failure")).mapFailure { finalFailure } == Try.failure(finalFailure)
    }

    def "successful or else does not map failure"() {
        expect:
        Try.successful(10).getOrMapFailure({ assert false })
    }

    def "failed or else maps failure"() {
        def failure = new RuntimeException("failure")
        boolean failureInvoked = false
        when:
        Try.failure(failure).getOrMapFailure({ failureInvoked = true; assert it == failure })
        then:
        failureInvoked
    }

    def "successful or else processes value"() {
        def value = "10"
        boolean successInvoked = false
        when:
        Try.successful(value).ifSuccessfulOrElse({ successInvoked = true; assert it == value }, { assert false })
        then:
        successInvoked
    }

    def "failed or else processes failure"() {
        def failure = new RuntimeException("failure")
        boolean failureInvoked = false
        when:
        Try.failure(failure).ifSuccessfulOrElse({ assert false }, { failureInvoked = true; assert it == failure })
        then:
        failureInvoked
    }

    def "can hold null as success"() {
        expect:
        success.isSuccessful()
        success.get() == null

        where:
        success << [Try.successful(null), Try.ofFailable { null }]
    }

    def "can compare null-holding try with other"() {
        when:
        def a = Try.successful(null)
        def b = Try.successful(null)

        then:
        a !== b
        a.equals(a)
        a.equals(b)
        b.equals(a)
    }

    def "can compare null-holding try with non-null"() {
        when:
        def a = Try.successful(null as String)
        def b = Try.successful("value")

        then:
        !a.equals(b)
        !b.equals(a)
    }

    def "null try has a hashcode"() {
        when:
        def a = Try.successful(null)
        a.hashCode()

        then:
        noExceptionThrown()
    }

    def "cannot hold null as failure"() {
        when:
        Try.failure(null)

        then:
        NullPointerException ex = thrown()
        ex.message == "null failure is not allowed"
    }

    def "can map null try"() {
        when:
        def mapped = Try.successful(null).map {
            assert it == null
            "value"
        }

        then:
        mapped.get() == "value"
    }

    def "can map successful to null"() {
        when:
        def mapped = Try.successful("value").map {
            null
        }

        then:
        mapped.isSuccessful()
        mapped.get() == null
    }

    def "can tryMap successful to null"() {
        when:
        def mapped = Try.successful("value").tryMap {
            null
        }

        then:
        mapped.isSuccessful()
        mapped.get() == null
    }

    def "can flatMap null try"() {
        when:
        def mapped = Try.successful(null).flatMap {
            assert it == null
            Try.successful("value")
        }

        then:
        mapped.get() == "value"
    }
}
