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
    val packagePrefix: String?,

    /**
     * The classes to keep in the output. These are not renamed.
     */
    val keepClasses: NameMatcher,

    /**
     * Create directories for entries in the output JAR?
     */
    val keepDirectories: Boolean,

    /**
     * The classes to not rename, if they are present.
     */
    val unshadedClasses: NameMatcher,

    /**
     * The classes to exclude from the output. These are not renamed.
     */
    val excludeClasses: NameMatcher,

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
        private var shadowPackage: String? = null
        private var keepDirectories = false
        private val keepClasses = mutableSetOf<String>()
        private val keepPackages = mutableSetOf<String>()
        private val unshadedPackages = mutableSetOf<String>()
        private val excludePackages = mutableSetOf<String>()
        private val keepResources = mutableSetOf<String>()
        private val excludeResources = mutableSetOf<String>()
        private val excludeResourcesFromDependencies = mutableSetOf<String>()

        fun renameClassesIntoPackage(prefix: String): Builder {
            this.shadowPackage = prefix
            return this
        }

        fun keepClasses(classes: Iterable<String>): Builder {
            keepClasses.addAll(classes)
            return this
        }

        fun keepPackages(packages: Iterable<String>): Builder {
            keepPackages.addAll(packages)
            return this
        }

        fun keepDirectories(): Builder {
            keepDirectories = true
            return this
        }

        fun doNotRenamePackages(packages: Iterable<String>): Builder {
            unshadedPackages.addAll(packages)
            return this
        }

        fun excludePackages(packages: Iterable<String>): Builder {
            excludePackages.addAll(packages)
            return this
        }

        fun keepResources(resources: Iterable<String>): Builder {
            keepResources.addAll(resources)
            return this
        }

        fun excludeResources(resources: Iterable<String>): Builder {
            excludeResources.addAll(resources)
            return this
        }

        fun excludeResourcesFromDependencies(resources: Iterable<String>): Builder {
            excludeResourcesFromDependencies.addAll(resources)
            return this
        }

        fun build(): PackagingParameters {
            return PackagingParameters(
                shadowPackage,
                NameMatcher.anyOf(NameMatcher.classNames(keepClasses), NameMatcher.packages(keepPackages)),
                keepDirectories,
                NameMatcher.packages(unshadedPackages),
                NameMatcher.packages(excludePackages),
                if (keepResources.isNotEmpty()) NameMatcher.not(NameMatcher.patterns(keepResources)) else NameMatcher.patterns(excludeResources),
                if (keepResources.isNotEmpty()) NameMatcher.not(NameMatcher.patterns(keepResources)) else NameMatcher.patterns(excludeResources + excludeResourcesFromDependencies)
            )
        }
    }
}
