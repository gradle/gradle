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

package org.gradle.internal.instantiation.generator

import spock.lang.Issue
import spock.lang.Specification

/**
 * These fixtures are compiled by groovyc, so they exercise the dynamic-Groovy shape, where
 * {@code super.getX()} becomes a reflective {@code ScriptBytecodeAdapter.invokeMethodOnSuper0}
 * call rather than an INVOKESPECIAL. The javac shape is covered by FreefairAspectJPluginSmokeTest,
 * whose plugin delegates to super and must not be reported.
 */
@Issue("https://github.com/gradle/gradle/issues/25421")
class SuperDelegatingAccessorsTest extends Specification {

    static class Base {
        String getThing() { return "base" }
    }

    static class Delegating extends Base {
        @Override
        String getThing() { return super.getThing() }
    }

    static class AddsLogic extends Base {
        @Override
        String getThing() { return super.getThing() + "!" }
    }

    static class IgnoresSuper extends Base {
        @Override
        String getThing() { return "own" }
    }

    static class ReadsField extends Base {
        private String other = "x"

        @Override
        String getThing() { return other }
    }

    static class Branches extends Base {
        @Override
        String getThing() { return System.getProperty("x") == null ? super.getThing() : "other" }
    }

    def "recognises a body that only delegates to super"() {
        expect:
        SuperDelegatingAccessors.isPureSuperDelegation(Delegating.getDeclaredMethod("getThing"))
    }

    def "does not recognise #type.simpleName"() {
        expect:
        !SuperDelegatingAccessors.isPureSuperDelegation(type.getDeclaredMethod("getThing"))

        where:
        type << [AddsLogic, IgnoresSuper, ReadsField, Branches]
    }

    def "does not recognise a method whose bytecode cannot be read"() {
        given:
        // Array classes have no class file to load
        def method = Object.getDeclaredMethod("toString")

        expect:
        !SuperDelegatingAccessors.isPureSuperDelegation(method)
    }
}
