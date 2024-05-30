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

sealed interface NameMatcher {
    /**
     * Names are '/' separated
     */
    fun matches(name: String): Boolean

    companion object {
        fun className(name: String): NameMatcher {
            return Exact(name.replace(".", "/"))
        }

        fun classNames(names: Iterable<String>): NameMatcher {
            return anyOf(names.map { className(it) })
        }

        fun pattern(pattern: String): NameMatcher {
            return if (pattern.contains("*")) {
                Pattern(Regex(pattern.replace("*", ".*")))
            } else {
                Exact(pattern)
            }
        }

        fun patterns(patterns: Iterable<String>): NameMatcher {
            return anyOf(patterns.map { pattern(it) })
        }

        /**
         * Matches all classes and resources in the given package and all of its sub-packages.
         */
        fun packageHierarchy(packageName: String): NameMatcher {
            val packagePath = packageName.replace('.', '/')
            return Prefix("$packagePath/")
        }

        fun packages(packages: Iterable<String>): NameMatcher {
            return anyOf(packages.map { packageHierarchy(it) })
        }

        fun not(matcher: NameMatcher): NameMatcher = Not(matcher)

        fun anyOf(vararg matchers: NameMatcher) = anyOf(matchers.toList())

        fun anyOf(matchers: List<NameMatcher>): NameMatcher {
            return if (matchers.isEmpty()) {
                Nothing
            } else if (matchers.size == 1) {
                matchers.first()
            } else {
                Any(matchers)
            }
        }
    }

    object Nothing : NameMatcher {
        override fun matches(name: String): Boolean {
            return false
        }
    }

    private class Exact(private val name: String) : NameMatcher {
        override fun matches(name: String): Boolean {
            return name == this.name
        }
    }

    private class Pattern(private val pattern: Regex) : NameMatcher {
        override fun matches(name: String): Boolean {
            return pattern.matches(name)
        }
    }

    private class Prefix(private val prefix: String) : NameMatcher {
        override fun matches(name: String): Boolean {
            return name.startsWith(prefix)
        }
    }

    private class Not(private val matcher: NameMatcher) : NameMatcher {
        override fun matches(name: String): Boolean {
            return !matcher.matches(name)
        }
    }

    private class Any(private val matches: List<NameMatcher>) : NameMatcher {
        override fun matches(name: String): Boolean {
            return matches.any { it.matches(name) }
        }
    }
}
