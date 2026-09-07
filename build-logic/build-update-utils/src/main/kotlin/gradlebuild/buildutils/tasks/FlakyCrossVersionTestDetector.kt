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

package gradlebuild.buildutils.tasks

import java.io.File


/**
 * Detects whether a subproject has a cross-version test that `-PflakyTests=ONLY` could select.
 *
 * That filter is a JUnit Platform tag include, evaluated during discovery inside the test JVM, so Gradle
 * cannot tell up front that a task will select nothing — it forks a JVM for every one regardless.
 * Cross-version test tasks are registered per tested Gradle version per subproject, so scheduling the
 * subprojects without any `@Flaky` cross-version test costs over a thousand JVM forks that discover
 * nothing and exit.
 *
 * The match is deliberately loose: a false positive only restores the old behaviour for one subproject,
 * while a false negative would silently drop a test from the quarantine build.
 */
object FlakyCrossVersionTestDetector {

    private
    const val FLAKY_ANNOTATION = "org.gradle.test.fixtures.Flaky"

    private
    val SOURCE_EXTENSIONS = setOf("groovy", "java", "kt")

    fun hasFlakyCrossVersionTest(subprojectDir: File): Boolean {
        val dir = File(subprojectDir, "src/crossVersionTest")
        if (!dir.isDirectory) {
            return false
        }
        return dir.walk()
            .filter { it.isFile && it.extension in SOURCE_EXTENSIONS }
            .any { file -> file.useLines { lines -> lines.any { "@Flaky" in it || FLAKY_ANNOTATION in it } } }
    }
}
