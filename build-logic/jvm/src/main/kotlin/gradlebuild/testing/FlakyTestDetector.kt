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

package gradlebuild.testing

import java.io.File


/**
 * Whether a test source set contains tests that `-PflakyTests=ONLY` could select.
 *
 * The answer decides only whether a [org.gradle.api.tasks.testing.Test] task runs at all, never which
 * tests it runs - that stays with the tag and annotation filters. The two error directions are not
 * symmetric: a false positive costs one forked JVM that discovers nothing and passes, exactly what
 * every task does today, while a false negative skips a task and drops a flaky test from the only
 * build that runs it.
 *
 * So this is the crudest check that works: does any source file mention the annotation's fully
 * qualified name? Every file applying it contains that name, as an import for `@Flaky` and for
 * `@Category(Flaky)` alike, or spelled out where it is applied. The bare type name would not do -
 * `com.Flaky` appears as a fixture class name in expected JUnit XML - and anything more precise would
 * mean parsing comments, string literals and type declarations to buy nothing this decision needs.
 *
 * One false negative is not covered and cannot be, because the scan never leaves the source set: a
 * `@Flaky` method inherited from a base class declared in `testFixtures`, or in the `main` source set
 * of a fixtures project such as `internal-integ-testing`, is selected at run time in subclasses whose
 * own sources never mention it. Nothing in the build does this today.
 */
object FlakyTestDetector {

    private
    const val FLAKY_ANNOTATION = "org.gradle.test.fixtures.Flaky"

    private
    val SOURCE_EXTENSIONS = setOf("groovy", "java", "kt")

    fun hasFlakyTests(sources: Iterable<File>): Boolean =
        sources.any { it.isFile && it.extension in SOURCE_EXTENSIONS && it.mentionsFlakyAnnotation() }

    private
    fun File.mentionsFlakyAnnotation(): Boolean =
        useLines { lines -> lines.any { FLAKY_ANNOTATION in it } }
}
