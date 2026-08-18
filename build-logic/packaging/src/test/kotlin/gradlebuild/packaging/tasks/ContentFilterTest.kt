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

package gradlebuild.packaging.tasks

import gradlebuild.packaging.tasks.ContentFilter.API_ONLY
import gradlebuild.packaging.tasks.ContentFilter.SKIP
import gradlebuild.packaging.tasks.ContentFilter.VERBATIM
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable


internal
class ContentFilterTest {

    @Test
    fun classifies_the_entries_of_a_classes_directory() {
        val expectations = mapOf(
            "org/gradle/api/Task.class" to API_ONLY,
            // Extraction keeps the module and the package annotations, so these need no copy
            "module-info.class" to API_ONLY,
            "org/gradle/api/package-info.class" to API_ONLY,
            "META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule" to VERBATIM,
            "META-INF/services/org.codehaus.groovy.transform.ASTTransformation" to VERBATIM,
            "META-INF/org.gradle.core.kotlin_module" to VERBATIM,
            "META-INF/MANIFEST.MF" to SKIP,
            "META-INF/services/some.other.Service" to SKIP
        )

        assertAll(expectations.map { (relativePath, expected) ->
            Executable {
                assertEquals(expected, contentFilterFor(relativePath), relativePath)
            }
        })
    }
}
