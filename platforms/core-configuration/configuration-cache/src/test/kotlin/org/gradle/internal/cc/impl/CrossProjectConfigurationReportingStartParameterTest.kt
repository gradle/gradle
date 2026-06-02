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

package org.gradle.internal.cc.impl

import org.gradle.StartParameter
import org.gradle.api.internal.StartParameterInternal
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class CrossProjectConfigurationReportingStartParameterTest {

    /**
     * [CrossProjectConfigurationReportingStartParameter] is a subclass of [StartParameterInternal]
     * (not an interface delegate), so a method it does not override silently reads the wrapper's
     * own super-state instead of the delegate's — wrong values for cross-project reads and missing
     * Isolated Projects reporting for writes, with no compile error.
     *
     * This test turns that silent bug into a failure: whenever a public method is added to
     * [StartParameter] or [StartParameterInternal], an override delegating to `delegate` (and
     * calling `onMutation(...)` for mutators) must be added to the wrapper.
     *
     * Protected methods are intentionally out of scope: the `prepareNewInstance`/`prepareNewBuild`
     * hooks are only invoked by `newInstance()`/`newBuild()`, which the wrapper overrides to
     * delegate, so the wrapper's own hooks are never reached.
     */
    @Test
    fun `overrides every public method of StartParameter and StartParameterInternal`() {
        val wrapper = CrossProjectConfigurationReportingStartParameter::class.java

        val missing = listOf(StartParameter::class.java, StartParameterInternal::class.java)
            .flatMap { it.declaredMethods.asIterable() }
            .filter { it.isOverridable() }
            .filterNot { wrapper.declaresOverrideOf(it) }
            .map { it.signature() }
            .distinct()
            .sorted()

        assertTrue(
            "CrossProjectConfigurationReportingStartParameter must override every public method of " +
                "StartParameter and StartParameterInternal, delegating to `delegate` and reporting " +
                "mutations via `onMutation(...)`. Missing overrides:\n" +
                missing.joinToString("\n") { "  - $it" },
            missing.isEmpty()
        )
    }

    private
    fun Method.isOverridable(): Boolean =
        Modifier.isPublic(modifiers) &&
            !Modifier.isStatic(modifiers) &&
            !Modifier.isFinal(modifiers) &&
            !isSynthetic

    private
    fun Class<*>.declaresOverrideOf(method: Method): Boolean =
        declaredMethods.any { it.name == method.name && it.parameterTypes.contentEquals(method.parameterTypes) }

    private
    fun Method.signature(): String =
        "${declaringClass.simpleName}.$name(${parameterTypes.joinToString(", ") { it.simpleName }})"
}
