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
 * @param forceRemovePackages packages taken off before minifier runs, with their classes and their resources
 * @param erasedMethods `some.Class#method` to replace with a stub
 */
data class MinifySpec(
    val keepClasses: Set<String>,
    val excludedClasses: Set<String> = emptySet(),
    val forceRemovePackages: Set<String> = emptySet(),
    val erasedMethods: Set<String> = emptySet()
) : Serializable {

    val needsPreprocessing: Boolean
        get() = forceRemovePackages.isNotEmpty() || erasedMethods.isNotEmpty()
}
