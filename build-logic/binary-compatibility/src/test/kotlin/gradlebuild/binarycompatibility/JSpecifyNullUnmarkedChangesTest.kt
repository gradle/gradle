/*
 * Copyright 2025 the original author or authors.
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

package gradlebuild.binarycompatibility

import org.junit.Test

class JSpecifyNullUnmarkedChangesTest : AbstractBinaryCompatibilityTest() {

    private val nullableAnnotationName = "org.jspecify.annotations.Nullable"
    private val nullUnmarkedAnnotationName = "org.jspecify.annotations.NullUnmarked"

    @Test
    fun `from non-null returning to null-unmarked returning is breaking`() {

        checkNotBinaryCompatibleJava(
            v1 = """
                public class Source {
                    public String foo() { return "bar"; }
                    public String[] baz() { return new String[] {"some"}; }
                }
            """,
            v2 = """
                public class Source {
                    @$nullUnmarkedAnnotationName public String foo() { return "bar"; }
                    public @$nullUnmarkedAnnotationName String[] baz() { return new String[] {"some", null}; }
                }
            """
        ) {
            assertHasErrors(
                "Method com.example.Source.foo(): From non-null returning to null-unmarked returning breaking change.",
                "Method com.example.Source.baz(): From non-null returning to null-unmarked returning breaking change.",
            )
            assertHasNoWarning()
            assertHasNoInformation()
        }
    }

    @Test
    fun `from null-unmarked returning to non-null returning is not breaking`() {

        checkBinaryCompatibleJava(
            v1 = """
                public class Source {
                    @$nullUnmarkedAnnotationName public String foo(String bar) { return bar; }
                    public @$nullUnmarkedAnnotationName String[] baz(String bar) { return new String[] {bar, null}; }
                }
            """,
            v2 = """
                public class Source {
                    public String foo(String bar) { return bar; }
                    public String[] baz(String bar) { return new String[] {bar}; }
                }
            """
        ) {
            assertHasNoError()
            assertHasWarnings(
                "Method com.example.Source.foo(java.lang.String): Return nullability changed from null-unmarked to non-nullable",
                "Method com.example.Source.baz(java.lang.String): Return nullability changed from null-unmarked to non-nullable",
            )
            assertHasNoInformation()
        }
    }

    @Test
    fun `from null-unmarked returning to nullable returning is breaking`() {

        checkNotBinaryCompatibleJava(
            v1 = """
                public class Source {
                    @$nullUnmarkedAnnotationName public String foo() { return "bar"; }
                    public @$nullUnmarkedAnnotationName String[] baz() { return new String[] {"some"}; }
                }
            """,
            v2 = """
                public class Source {
                    @$nullableAnnotationName public String foo() { return "bar"; }
                    public @$nullableAnnotationName String[] baz() { return new String[] {"some", null}; }
                }
            """
        ) {
            assertHasErrors(
                "Method com.example.Source.foo(): From null-unmarked returning to null returning breaking change.",
                "Method com.example.Source.baz(): From null-unmarked returning to null returning breaking change.",
            )
            assertHasNoWarning()
            assertHasNoInformation()
        }
    }

    @Test
    fun `from nullable returning to null-unmarked returning is not breaking`() {

        checkBinaryCompatibleJava(
            v1 = """
                public class Source {
                    @$nullableAnnotationName public String foo(String bar) { return bar; }
                    public @$nullableAnnotationName String[] baz(String bar) { return new String[] {bar, null}; }
                }
            """,
            v2 = """
                public class Source {
                    @$nullUnmarkedAnnotationName public String foo(String bar) { return bar; }
                    public @$nullUnmarkedAnnotationName String[] baz(String bar) { return new String[] {bar}; }
                }
            """
        ) {
            assertHasNoError()
            assertHasWarnings(
                "Method com.example.Source.foo(java.lang.String): Return nullability changed from nullable to null-unmarked",
                "Method com.example.Source.baz(java.lang.String): Return nullability changed from nullable to null-unmarked",
            )
            assertHasNoInformation()
        }
    }
}
