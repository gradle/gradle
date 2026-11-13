/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.provider.plugins.precompiled

import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.provider.plugins.accessorTypePrecedenceSequence
import org.gradle.kotlin.dsl.provider.plugins.ancestorClassesIncludingSelf
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import kotlin.reflect.KClass


class AccessorTypePrecedenceTest {

    @Test
    fun `classes before interfaces`() {
        assertAccessorTypePrecedenceOf<ChooseA>(
            ChooseA::class,
            Base::class,
            Core::class,
            A::class, // because it appears first than B when walking up the tree starting at ChooseA
            B::class,
            ExtensionAware::class
        )
    }

    @Test
    fun `classes before interfaces, subtypes before supertypes`() {
        assertAccessorTypePrecedenceOf<ChooseB>(
            ChooseB::class,
            Base::class,
            Core::class,
            B::class, // because B extends ExtensionAware, even though ExtensionAware appears first
            ExtensionAware::class
        )
    }

    @Test
    fun `ancestorClassesIncludingSelf does not include Any`() {
        assertTypeSequence(
            ChooseA::class.java.ancestorClassesIncludingSelf,
            ChooseA::class,
            Base::class,
            Core::class
        )
    }

    private
    inline fun <reified T> assertAccessorTypePrecedenceOf(vararg types: KClass<*>) {
        assertTypeSequence(
            T::class.java.accessorTypePrecedenceSequence(),
            *types
        )
    }

    private
    fun assertTypeSequence(sequence: Sequence<Class<*>>, vararg types: KClass<*>) {
        assertThat(
            sequence.mapTo(mutableListOf()) { it.simpleName },
            equalTo(types.map { it.simpleName })
        )
    }

    abstract class ChooseA : Base(), ExtensionAware, A

    abstract class ChooseB : Base(), ExtensionAware

    abstract class Base : Core(), B

    open class Core

    interface A : ExtensionAware

    interface B : ExtensionAware
}
