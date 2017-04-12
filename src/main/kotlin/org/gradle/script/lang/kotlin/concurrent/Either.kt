/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.script.lang.kotlin.concurrent


/**
 * Represents values with two possibilities.
 */
sealed class Either<out L, out R> {

    abstract fun <T> fold(left: (L) -> T, right: (R) -> T): T

    data class Left<out L, out R>(val value: L) : Either<L, R>() {
        override fun <T> fold(left: (L) -> T, right: (R) -> T): T = left(value)
    }

    data class Right<out L, out R>(val value: R) : Either<L, R>() {
        override fun <T> fold(left: (L) -> T, right: (R) -> T): T = right(value)
    }
}


/**
 * Constructs a [Either.Left] value.
 */
fun <L, R> left(value: L): Either<L, R> = Either.Left<L, R>(value)


/**
 * Constructs a [Either.Right] value.
 */
fun <L, R> right(value: R): Either<L, R> = Either.Right<L, R>(value)


