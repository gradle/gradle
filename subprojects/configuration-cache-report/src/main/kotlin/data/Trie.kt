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

package data


@Suppress("experimental_feature_warning")
value class Trie<T>(

    private
    val nestedMaps: Map<T, Any>

) {
    companion object {

        fun <T> from(paths: Sequence<List<T>>) =
            Trie(nestedMapsFrom(paths))
    }

    val entries: Sequence<Pair<T, Trie<T>>>
        get() = nestedMaps.asSequence().map { (label, subTrie) ->
            label to Trie(subTrie.uncheckedCast())
        }

    val size: Int
        get() = nestedMaps.size
}


private
fun <T> nestedMapsFrom(paths: Sequence<List<T>>): Map<T, Any> {
    val root = hashMapOf<T, HashMap<T, Any>>()
    for (path in paths) {
        var node = root
        for (segment in path) {
            node = node.getOrPut(segment) {
                hashMapOf()
            }.uncheckedCast()
        }
    }
    return root
}


private
inline fun <reified T> Any.uncheckedCast(): T = this as T
