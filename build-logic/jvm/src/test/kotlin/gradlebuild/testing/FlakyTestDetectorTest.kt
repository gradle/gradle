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

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File


internal
class FlakyTestDetectorTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun finds_an_imported_annotation() {
        val source = sourceFile(
            "FooTest.groovy",
            """
            import org.gradle.test.fixtures.Flaky
            @Flaky(because = "issue")
            class FooTest {}
            """.trimIndent()
        )

        assertTrue(FlakyTestDetector.hasFlakyTests(listOf(source)))
    }

    @Test
    fun finds_an_annotation_written_out_in_full() {
        val source = sourceFile(
            "BarTest.java",
            """
            public class BarTest {
                @org.gradle.test.fixtures.Flaky(because = "issue")
                public void test() {}
            }
            """.trimIndent()
        )

        assertTrue(FlakyTestDetector.hasFlakyTests(listOf(source)))
    }

    @Test
    fun finds_a_junit4_category() {
        val source = sourceFile(
            "CategorisedTest.java",
            """
            import org.gradle.test.fixtures.Flaky;
            @Category(Flaky.class)
            public class CategorisedTest {}
            """.trimIndent()
        )

        assertTrue(FlakyTestDetector.hasFlakyTests(listOf(source)))
    }

    @Test
    fun ignores_sources_without_the_annotation() {
        val source = sourceFile(
            "PlainTest.groovy",
            """
            class PlainTest {}
            """.trimIndent()
        )

        assertFalse(FlakyTestDetector.hasFlakyTests(listOf(source)))
    }

    @Test
    fun ignores_the_bare_type_name_used_as_test_data() {
        // JUnitXmlResultWriterMergeRerunSpec uses "com.Flaky" as a class name in expected XML.
        val source = sourceFile(
            "ResultWriterTest.groovy",
            """
            class ResultWriterTest {
                def "writes reruns"() {
                    expect: render("com.Flaky") == '<testsuite name="com.Flaky"/>'
                }
            }
            """.trimIndent()
        )

        assertFalse(FlakyTestDetector.hasFlakyTests(listOf(source)))
    }

    @Test
    fun ignores_files_that_are_not_sources() {
        val source = sourceFile("notes.txt", "org.gradle.test.fixtures.Flaky")

        assertFalse(FlakyTestDetector.hasFlakyTests(listOf(source)))
    }

    @Test
    fun one_annotated_source_is_enough() {
        val plain = sourceFile("PlainTest.groovy", "class PlainTest {}")
        val flaky = sourceFile(
            "FlakyTest.groovy",
            """
            import org.gradle.test.fixtures.Flaky
            @Flaky(because = "issue")
            class FlakyTest {}
            """.trimIndent()
        )

        assertTrue(FlakyTestDetector.hasFlakyTests(listOf(plain, flaky)))
    }

    private
    fun sourceFile(relativePath: String, contents: String): File {
        val file = tempDir.resolve(relativePath)
        file.parentFile.mkdirs()
        file.writeText(contents)
        return file
    }
}
