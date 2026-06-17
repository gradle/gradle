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

package org.gradle.api.internal

import org.gradle.StartParameter
import org.gradle.internal.buildoption.Option
import spock.lang.Specification

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.time.Duration

/**
 * Verifies that every mutating method of {@link StartParameter} and {@link StartParameterInternal} is
 * instrumented to notify the mutation listener (via {@code onMutableCall}). Reflection makes this an
 * instrumentation-completeness check: a newly added setter that forgets the hook fails this test, with
 * no table to remember to update.
 */
class StartParameterMutationInstrumentationTest extends Specification {

    // Mutators that deliberately do not notify, plus the listener-wiring method itself (a setter by name).
    private static final Set<String> NOT_NOTIFYING = [
        // Task requests are exempt from violation reporting for now: tooling model builders still
        // legitimately replace the tasks to run while the build is already running.
        "setTaskNames(Iterable)",
        "setTaskRequests(Iterable)",
        // Infrastructure for installing the listener, not tracked state.
        "setMutationListener(Consumer)",
    ] as Set

    def "every mutating method of StartParameter(Internal) notifies the mutation listener"() {
        given:
        def notified = []
        def parameter = new StartParameterInternal()
        parameter.setMutationListener { notified << it }

        expect:
        // Guard against the reflective enumeration silently matching nothing (a vacuous pass).
        def methods = mutatingMethods()
        println "Checking ${methods.size()} mutating methods for onMutableCall instrumentation"
        methods.size() >= 30

        and:
        methods.each { method ->
            notified.clear()
            try {
                method.invoke(parameter, argumentsFor(method))
            } catch (InvocationTargetException ignored) {
                // A setter may validate its argument and throw; that is fine as long as the mutation hook
                // has already fired, since the contract is "notify first, then mutate".
            }
            assert !notified.isEmpty():
                "${signature(method)} does not call onMutableCall(). Instrument it, or if the mutation " +
                    "is intentionally not reported, add it to NOT_NOTIFYING."
        }
    }

    private static List<Method> mutatingMethods() {
        (StartParameter.methods.toList() + StartParameterInternal.methods.toList())
            .findAll { isCandidateMutator(it) }
            .unique { signature(it) }
            .findAll { !NOT_NOTIFYING.contains(signature(it)) }
            .sort { signature(it) }
    }

    private static boolean isCandidateMutator(Method m) {
        if (Modifier.isStatic(m.modifiers) || m.synthetic) {
            return false
        }
        m.name.startsWith("set") ||
            m.name.startsWith("add") ||
            m.name in ["doNotSearchUpwards", "useEmptySettings", "includeBuild", "enableProblemReportGeneration"]
    }

    private static Object[] argumentsFor(Method m) {
        m.parameterTypes.collect { sampleValue(it) } as Object[]
    }

    private static Object sampleValue(Class<?> type) {
        if (type == boolean.class) {
            return false
        }
        if (type == int.class) {
            return 1
        }
        if (type == File.class) {
            return new File(".")
        }
        if (type == String.class) {
            return ""
        }
        if (type == Duration.class) {
            return Duration.ZERO
        }
        if (type == Option.Value.class) {
            return Option.Value.defaultValue(false)
        }
        if (type.isEnum()) {
            return type.enumConstants[0]
        }
        if (Map.isAssignableFrom(type)) {
            return [:]
        }
        if (Iterable.isAssignableFrom(type)) {
            return []
        }
        if (type.isPrimitive()) {
            throw new IllegalArgumentException("No sample value for primitive ${type.name}; extend sampleValue()")
        }
        // Any other reference type: null is enough, since onMutableCall fires before the argument is used
        // and we tolerate the setter throwing afterwards.
        return null
    }

    private static String signature(Method m) {
        "${m.name}(${m.parameterTypes.collect { it.simpleName }.join(', ')})"
    }
}
