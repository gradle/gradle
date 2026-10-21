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

package gradlebuild.basics.transforms

import java.io.Serializable


/**
 * How a library of the distribution is minified.
 *
 * @param keepClasses class name patterns to keep, along with everything they reach
 * @param excludedClasses class name patterns to subtract from [keepClasses]
 * @param removePackages packages taken off the input, with their classes and their resources
 * @param sideEffectFreeCalls `some.Class#void method()` whose calls are dropped, for what they reach
 * @param removeUnreachable whether to drop what no kept class reaches, which a library that resolves
 * its own classes by name cannot have
 * @param dropLocalVariables drops the local variable tables, which only a debugger reads
 */
data class MinifySpec(
    val keepClasses: Set<String> = emptySet(),
    val excludedClasses: Set<String> = emptySet(),
    val removePackages: Set<String> = emptySet(),
    val sideEffectFreeCalls: Set<String> = emptySet(),
    val removeUnreachable: Boolean = true,
    val dropLocalVariables: Boolean = false
) : Serializable {

    init {
        require(!removeUnreachable || keepClasses.isNotEmpty()) { "Nothing to keep in $this" }
    }
}
