/*
 * Copyright 2025 the original author or authors.
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

package gradlebuild.runtimes

import org.gradle.api.attributes.Attribute

object TargetRuntimesAttribute {

    val ATTRIBUTE: Attribute<String> = Attribute.of("gradlebuild.target-runtimes", String::class.java)

    const val STARTUP = "STARTUP"

    const val WRAPPER = "WRAPPER"

    const val WORKER = "WORKER"

    const val CLIENT = "CLIENT"

    const val DAEMON = "DAEMON"

    private val ALL_RUNTIMES = setOf(STARTUP, WRAPPER, WORKER, CLIENT, DAEMON)

    fun encode(runtimes: Collection<String>): String {
        val invalidRuntimes = runtimes.filterNot { it in ALL_RUNTIMES }
        require(invalidRuntimes.isEmpty()) {
            "Invalid runtimes: $invalidRuntimes. Valid runtimes are: $ALL_RUNTIMES"
        }
        return runtimes.sorted().joinToString(",")
    }

    fun decode(encoded: String): Set<String> {
        val split = encoded.split(",")
        val invalidRuntimes = split.filterNot { it in ALL_RUNTIMES }
        require(invalidRuntimes.isEmpty()) {
            "Invalid runtimes: $invalidRuntimes. Valid runtimes are: $ALL_RUNTIMES"
        }
        return split
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
