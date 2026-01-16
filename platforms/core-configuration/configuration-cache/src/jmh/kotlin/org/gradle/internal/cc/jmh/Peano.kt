/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.jmh


internal
sealed class Peano {

    companion object {

        fun fromInt(n: Int): Peano = (0 until n).fold(Z as Peano) { acc, _ -> S(acc) }
    }

    fun toInt(): Int = sequence().count() - 1

    object Z : Peano() {
        override fun toString() = "Z"
    }

    data class S(val n: Peano) : Peano() {
        override fun toString() = "S($n)"
    }

    private
    fun sequence() = generateSequence(this) { previous ->
        when (previous) {
            is Z -> null
            is S -> previous.n
        }
    }
}
