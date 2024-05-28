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

package gradlebuild.basics.classanalysis

class PackagingParameters(
    /**
     * The classes to keep in the output. These are not renamed.
     */
    val keepClasses: NameMatcher,

    /**
     * Resources from the main JAR to exclude from the output.
     */
    val excludeResources: NameMatcher,

    /**
     * Resources from dependencies to exclude from the output.
     */
    val excludeResourcesFromDependencies: NameMatcher
) {
    class Builder {
        private val keepClasses = mutableSetOf<String>()
        private val exclude = mutableSetOf<String>()
        private val excludeFromDependencies = mutableSetOf<String>()

        fun keepClasses(classes: Iterable<String>): Builder {
            keepClasses.addAll(classes)
            return this
        }

        fun excludeResources(resources: Iterable<String>): Builder {
            exclude.addAll(resources)
            return this
        }

        fun excludeResourcesFromDependencies(resources: Iterable<String>): Builder {
            excludeFromDependencies.addAll(resources)
            return this
        }

        fun build() = PackagingParameters(NameMatcher.classNames(keepClasses), NameMatcher.patterns(exclude), NameMatcher.patterns(exclude + excludeFromDependencies))
    }
}
