/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.kotlin.dsl.support

import org.slf4j.Logger
import org.slf4j.LoggerFactory


internal
inline fun <reified T : Any> loggerFor(): Logger =
    LoggerFactory.getLogger(T::class.java)


internal
inline fun Logger.trace(msg: () -> String) {
    if (isTraceEnabled) trace(msg())
}


internal
inline fun Logger.debug(msg: () -> String) {
    if (isDebugEnabled) debug(msg())
}


internal
inline fun Logger.info(msg: () -> String) {
    if (isInfoEnabled) info(msg())
}


internal
inline fun Logger.error(msg: () -> String) {
    if (isErrorEnabled) error(msg())
}
