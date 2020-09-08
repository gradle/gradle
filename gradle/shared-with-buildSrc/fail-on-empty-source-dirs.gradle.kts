/*
 * Copyright 2020 the original author or authors.
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

// This script can not be moved to buildSrc/main/kotlin as it is also used by the buildSrc build.

failOnEmptySourceDirs()

fun failOnEmptySourceDirs() {
    // Empty source dirs produce cache misses, and are not caught by `git status`.
    // Fail if we find any.
    tasks.withType<SourceTask>().configureEach {
        doFirst {
            source.visit {
                if (file.isDirectory && file.listFiles()?.isEmpty() == true) {
                    throw IllegalStateException("Empty src dir found. This causes build cache misses. Please fix github.com/gradle/gradle/issues/2463.\nRun the following command to fix it.\nrmdir ${file.absolutePath}")
                }
            }
        }
    }
}
