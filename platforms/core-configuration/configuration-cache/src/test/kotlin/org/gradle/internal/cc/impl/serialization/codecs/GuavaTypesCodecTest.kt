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

package org.gradle.internal.cc.impl.serialization.codecs

import com.google.common.collect.BoundType
import com.google.common.collect.ConcurrentHashMultiset
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ImmutableSetMultimap
import com.google.common.collect.ImmutableSortedSet
import com.google.common.collect.Multiset
import com.google.common.collect.Ordering
import com.google.common.collect.TreeMultiset
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.extensions.stdlib.useToRun
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable


class GuavaTypesCodecTest : AbstractUserTypeCodecTest() {

    @Test
    fun `can handle ImmutableListMultimap`() {
        verifyRoundtripOf({
            ImmutableListMultimap.builder<String, Int>()
                .putAll("b", 3, 1, 3)
                .putAll("a", 2)
                .putAll("c", 1, 1)
                .build()
        }) { multimap ->
            assertThat(
                // equality of multimaps does not account for the order of the keys
                multimap.entries().map { it.key to it.value },
                equalTo(listOf("b" to 3, "b" to 1, "b" to 3, "a" to 2, "c" to 1, "c" to 1))
            )
            assertThat(multimap.get("b"), equalTo(ImmutableList.of(3, 1, 3)))
            assertThat(multimap.size(), equalTo(6))
        }
    }

    @Test
    fun `can handle ImmutableSetMultimap`() {
        verifyRoundtripOf({
            ImmutableSetMultimap.builder<String, Int>()
                .putAll("b", 3, 1, 3)
                .putAll("a", 2)
                .putAll("c", 1, 4)
                .build()
        }) { multimap ->
            assertThat(
                // equality of multimaps does not account for the order of the keys and values
                multimap.entries().map { it.key to it.value },
                equalTo(listOf("b" to 3, "b" to 1, "a" to 2, "c" to 1, "c" to 4))
            )
            assertThat(multimap.get("b"), not(instanceOf(ImmutableSortedSet::class.java)))
            assertThat(multimap.size(), equalTo(5))
        }
    }

    @Test
    fun `can handle ImmutableSetMultimap with ordered values`() {
        verifyRoundtripOf({
            ImmutableSetMultimap.builder<String, String>()
                .orderValuesBy(CaseInsensitiveReverseOrder())
                .putAll("b", "x", "Z", "y")
                .putAll("a", "W", "v")
                .build()
        }) { multimap ->
            assertThat(
                multimap.entries().map { it.key to it.value },
                equalTo(listOf("b" to "Z", "b" to "y", "b" to "x", "a" to "W", "a" to "v"))
            )
            multimap.asMap().values.forEach { values ->
                assertThat(
                    assertInstanceOf<ImmutableSortedSet<String>>(values).comparator(),
                    instanceOf(CaseInsensitiveReverseOrder::class.java)
                )
            }
            assertThat(multimap.containsEntry("b", "z"), equalTo(true))
        }
    }

    @Test
    fun `empty immutable multimaps are restored as singletons`() {
        assertThat(
            configurationCacheRoundtripOf(ImmutableListMultimap.of<String, Int>()),
            sameInstance(ImmutableListMultimap.of())
        )
        assertThat(
            configurationCacheRoundtripOf(ImmutableSetMultimap.of<String, Int>()),
            sameInstance(ImmutableSetMultimap.of())
        )
    }

    @Test
    fun `can handle immutable multimaps of beans and collections`() {
        verifyRoundtripOf({
            val shared = Bean("shared")
            val list = ImmutableListMultimap.builder<Any, Any>()
                .putAll(Bean("k1"), shared, Bean("v"), shared)
                .putAll(shared, arrayListOf(1, 2), hashMapOf("a" to shared))
                .build()
            val set = ImmutableSetMultimap.builder<Any, Any>()
                .putAll(shared, Bean("v"), shared)
                .putAll("nested", list, ImmutableSetMultimap.of("k", shared))
                .build()
            Pair(shared, set)
        }) { (shared, set) ->
            assertThat(set.keySet().toList(), equalTo(listOf(shared, "nested")))
            assertThat(set.get(shared).toList(), equalTo(listOf(Bean("v"), shared)))
            assertThat("it preserves identity of values", set.get(shared).last(), sameInstance(shared))
            assertThat("it preserves identity of keys", set.keySet().first(), sameInstance(shared))

            val nested = set.get("nested").toList()
            val list = nested[0].uncheckedCast<ImmutableListMultimap<Any, Any>>()
            assertThat(list.keySet().toList(), equalTo(listOf(Bean("k1"), shared)))
            assertThat(list.get(Bean("k1")), equalTo<List<*>>(listOf(shared, Bean("v"), shared)))
            list.get(Bean("k1")).let { values ->
                assertThat(values[0], sameInstance(shared))
                assertThat(values[2], sameInstance(shared))
            }
            assertThat(list.get(shared), equalTo<List<*>>(listOf(arrayListOf(1, 2), hashMapOf("a" to shared))))
            assertThat((list.get(shared)[1] as Map<*, *>)["a"], sameInstance(shared))

            val nestedSet = nested[1].uncheckedCast<ImmutableSetMultimap<Any, Any>>()
            assertThat(nestedSet.get("k").single(), sameInstance(shared))
        }
    }

    @Test
    fun `can handle circular references through immutable multimaps`() {
        val owner = Bean("owner")
        owner.reference = Pair(
            ImmutableListMultimap.of(owner, owner, "other", Bean("other", owner)),
            ImmutableSetMultimap.of(owner, owner, "other", Bean("other", owner))
        )
        configurationCacheRoundtripOf(owner).run {
            val (list, set) = reference!!.uncheckedCast<Pair<ImmutableListMultimap<Any, Bean>, ImmutableSetMultimap<Any, Bean>>>()
            listOf(list, set).forEach { multimap ->
                assertThat(multimap.size(), equalTo(2))
                assertThat(multimap.keySet().first(), sameInstance(this))
                assertThat(multimap.get(this).single(), sameInstance(this))
                assertThat(multimap.get("other").single().reference, sameInstance(this))
            }
        }
    }

    @Test
    fun `can handle immutable multimaps enclosing a circular reference to themselves`() {
        val owner = Bean("owner")
        val list = ImmutableListMultimap.of(owner, owner)
        val set = ImmutableSetMultimap.of(owner, owner)
        owner.reference = Pair(list, set)

        configurationCacheRoundtripOf(list).run {
            val restoredOwner = keySet().single()
            assertThat(get(restoredOwner).single(), sameInstance(restoredOwner))
            val (innerList, innerSet) = restoredOwner.reference!!.uncheckedCast<Pair<ImmutableListMultimap<Bean, Bean>, ImmutableSetMultimap<Bean, Bean>>>()
            // the inner multimap is a copy of the enclosing one, but there is only one owner
            assertThat(innerList, equalTo(this))
            assertThat(innerList.keySet().single(), sameInstance(restoredOwner))
            assertThat(innerList.values().single(), sameInstance(restoredOwner))
            assertThat(innerSet.keySet().single(), sameInstance(restoredOwner))
            assertThat(innerSet.values().single(), sameInstance(restoredOwner))
        }
        configurationCacheRoundtripOf(set).run {
            val restoredOwner = keySet().single()
            assertThat(get(restoredOwner).single(), sameInstance(restoredOwner))
            val (innerList, innerSet) = restoredOwner.reference!!.uncheckedCast<Pair<ImmutableListMultimap<Bean, Bean>, ImmutableSetMultimap<Bean, Bean>>>()
            assertThat(innerSet, equalTo(this))
            assertThat(innerSet.keySet().single(), sameInstance(restoredOwner))
            assertThat(innerSet.values().single(), sameInstance(restoredOwner))
            assertThat(innerList.keySet().single(), sameInstance(restoredOwner))
            assertThat(innerList.values().single(), sameInstance(restoredOwner))
        }
    }

    @Test
    fun `can handle TreeMultiset`() {
        verifyRoundtripOf({
            TreeMultiset.create<String>().apply {
                add("b", 3)
                add("a")
                add("c", Int.MAX_VALUE - 4)
            }
        }) { multiset ->
            assertThat(multiset.entrySet().map { it.element to it.count }, equalTo(listOf("a" to 1, "b" to 3, "c" to Int.MAX_VALUE - 4)))
            assertThat(multiset.size, equalTo(Int.MAX_VALUE))
            assertThat(multiset.comparator(), sameInstance<Comparator<*>>(Ordering.natural<String>()))
            assertThat("it remains mutable", multiset.add("a", 2), equalTo(1))
            assertThat(multiset.firstEntry()!!.count, equalTo(3))
        }
    }

    @Test
    fun `can handle TreeMultiset with comparator`() {
        verifyRoundtripOf({
            TreeMultiset.create(CaseInsensitiveReverseOrder()).apply {
                add("b", 3)
                add("A")
                add("C", 2)
            }
        }) { multiset ->
            assertThat(multiset.toList(), equalTo(listOf("C", "C", "b", "b", "b", "A")))
            assertThat(multiset.comparator(), instanceOf(CaseInsensitiveReverseOrder::class.java))
            assertThat(multiset.count("B"), equalTo(3))
            assertThat(multiset.headMultiset("b", BoundType.OPEN).toList(), equalTo(listOf("C", "C")))
        }
    }

    @Test
    fun `can handle TreeMultiset with null element`() {
        verifyRoundtripOf({
            TreeMultiset.create(Ordering.natural<String>().nullsFirst<String>()).apply {
                add("a", 2)
                add(null, 3)
            }
        }) { multiset ->
            assertThat(multiset.entrySet().map { it.element to it.count }, equalTo(listOf(null to 3, "a" to 2)))
        }
    }

    @Test
    fun `can handle TreeMultiset of beans`() {
        verifyRoundtripOf({
            val shared = Bean("b")
            Pair(
                shared,
                TreeMultiset.create(BeanNameOrder()).apply {
                    add(Bean("c", shared), 2)
                    add(shared, 3)
                    add(Bean("a", arrayListOf(shared)))
                }
            )
        }) { (shared, multiset) ->
            assertThat(multiset.entrySet().map { it.element.name to it.count }, equalTo(listOf("a" to 1, "b" to 3, "c" to 2)))
            val (a, b, c) = multiset.elementSet().toList()
            assertThat(b, sameInstance(shared))
            assertThat(c.reference, sameInstance<Any>(shared))
            assertThat((a.reference as List<*>).single(), sameInstance<Any>(shared))
        }
    }

    @Test
    fun `can handle ConcurrentHashMultiset`() {
        verifyRoundtripOf({
            ConcurrentHashMultiset.create<String>().apply {
                add("b", 3)
                add("a")
                add("c", Int.MAX_VALUE - 4)
            }
        }) { multiset ->
            assertThat(multiset.entrySet().associate { it.element to it.count }, equalTo(mapOf("a" to 1, "b" to 3, "c" to Int.MAX_VALUE - 4)))
            assertThat(multiset.size, equalTo(Int.MAX_VALUE))
            assertThat("it remains mutable", multiset.add("a", 2), equalTo(1))
            assertThat(multiset.remove("b", 3), equalTo(3))
            assertThat(multiset.elementSet(), equalTo(setOf("a", "c")))
        }
    }

    @Test
    fun `can handle ConcurrentHashMultiset of beans`() {
        verifyRoundtripOf({
            val shared = Bean("b")
            Pair(
                shared,
                ConcurrentHashMultiset.create<Any>().apply {
                    add(Bean("c", shared), 2)
                    add(shared, 3)
                    add(ImmutableListMultimap.of("a", shared))
                }
            )
        }) { (shared, multiset) ->
            assertThat(multiset.count(shared), equalTo(3))
            assertThat(multiset.count(Bean("c", shared)), equalTo(2))
            assertThat(multiset.count(ImmutableListMultimap.of("a", shared)), equalTo(1))
            assertThat(multiset.elementSet().size, equalTo(3))
            multiset.elementSet().forEach { element ->
                when (element) {
                    is ImmutableListMultimap<*, *> -> assertThat(element.values().single(), sameInstance<Any>(shared))
                    is Bean -> assertThat(element.reference ?: element, sameInstance<Any>(shared))
                    else -> throw AssertionError("Unexpected element $element")
                }
            }
        }
    }

    @Test
    fun `can handle empty multisets`() {
        verifyRoundtripOf({
            Pair(TreeMultiset.create<String>(), ConcurrentHashMultiset.create<String>())
        }) { (tree, concurrent) ->
            listOf<Multiset<String>>(tree, concurrent).forEach { multiset ->
                assertThat(multiset.isEmpty(), equalTo(true))
                assertThat(multiset.entrySet().isEmpty(), equalTo(true))
                multiset.add("a", 2)
                assertThat(multiset.count("a"), equalTo(2))
            }
            assertThat(tree, not(sameInstance<Any>(concurrent)))
        }
    }

    @Test
    fun `can handle circular references through TreeMultiset`() {
        val owner = Bean("b")
        owner.reference = TreeMultiset.create(BeanNameOrder()).apply {
            add(owner, 2)
            add(Bean("a", Bean("nested", owner)))
        }
        configurationCacheRoundtripOf(owner).run {
            val restored = reference!!.uncheckedCast<TreeMultiset<Bean>>()
            assertThat(restored.entrySet().map { it.element.name to it.count }, equalTo(listOf("a" to 1, "b" to 2)))
            assertThat(restored.lastEntry()!!.element, sameInstance(this))
            assertThat((restored.firstEntry()!!.element.reference as Bean).reference, sameInstance<Any>(this))
        }
    }

    @Test
    fun `can handle circular references through ConcurrentHashMultiset`() {
        val owner = IdentityBean("owner")
        owner.reference = ConcurrentHashMultiset.create<Any>().apply {
            add(owner, 2)
            add(IdentityBean("other", owner))
        }
        configurationCacheRoundtripOf(owner).run {
            val restored = assertInstanceOf<ConcurrentHashMultiset<Any>>(reference!!)
            assertThat(restored.size, equalTo(3))
            assertThat(restored.count(this), equalTo(2))
            val other = restored.elementSet().filterIsInstance<IdentityBean>().single { it !== this }
            assertThat(other.name, equalTo("other"))
            assertThat(other.reference, sameInstance<Any>(this))
        }
    }

    @Test
    fun `can handle multisets enclosing a circular reference to themselves`() {
        val treeOwner = Bean("owner")
        val tree = TreeMultiset.create(BeanNameOrder()).apply { add(treeOwner, 2) }
        treeOwner.reference = tree
        configurationCacheRoundtripOf(tree).run {
            val restoredOwner = elementSet().single()
            assertThat(count(restoredOwner), equalTo(2))
            // the inner multiset is a copy of the enclosing one, but there is only one owner
            val inner = restoredOwner.reference!!.uncheckedCast<TreeMultiset<Bean>>()
            assertThat(inner.elementSet().single(), sameInstance(restoredOwner))
            assertThat(inner.count(restoredOwner), equalTo(2))
        }

        val concurrentOwner = IdentityBean("owner")
        val concurrent = ConcurrentHashMultiset.create<IdentityBean>().apply { add(concurrentOwner, 2) }
        concurrentOwner.reference = concurrent
        configurationCacheRoundtripOf(concurrent).run {
            val restoredOwner = elementSet().single()
            assertThat(count(restoredOwner), equalTo(2))
            val inner = restoredOwner.reference!!.uncheckedCast<ConcurrentHashMultiset<IdentityBean>>()
            assertThat(inner.elementSet().single(), sameInstance(restoredOwner))
            assertThat(inner.count(restoredOwner), equalTo(2))
        }
    }

    /**
     * Verifies the configuration cache gives the same guarantees as Java serialization.
     */
    private
    fun <T : Any> verifyRoundtripOf(factory: () -> T, verify: (T) -> Unit) {
        verify(javaSerializationRoundtripOf(factory()))
        verify(configurationCacheRoundtripOf(factory()))
    }

    private
    fun <T : Any> javaSerializationRoundtripOf(graph: T): T =
        ByteArrayOutputStream().also { bytes ->
            ObjectOutputStream(bytes).useToRun {
                writeObject(graph)
            }
        }.let { bytes ->
            ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).readObject().uncheckedCast()
        }

    data class Bean(val name: String, var reference: Any? = null) : Serializable {
        override fun equals(other: Any?): Boolean = other is Bean && other.name == name
        override fun hashCode(): Int = name.hashCode()
        override fun toString(): String = "Bean($name)"
    }

    /**
     * Relies on identity for equality so it can be hashed before it is fully restored.
     */
    class IdentityBean(val name: String, var reference: Any? = null)

    class CaseInsensitiveReverseOrder : Comparator<String>, Serializable {
        override fun compare(left: String, right: String): Int =
            String.CASE_INSENSITIVE_ORDER.compare(right, left)
    }

    class BeanNameOrder : Comparator<Bean>, Serializable {
        override fun compare(left: Bean, right: Bean): Int =
            left.name.compareTo(right.name)
    }
}
