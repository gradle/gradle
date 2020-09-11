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
package gradlebuild.binarycompatibility

import org.junit.Test


class NullabilityChangesTest : AbstractBinaryCompatibilityTest() {

    @Test
    fun `from non-null returning to null returning is breaking (java)`() {

        checkNotBinaryCompatibleJava(
            v1 = """
                public class Source {
                    public String nonFinalField = "some";
                    public final String finalField = "some";
                    public String foo() { return "bar"; }
                }
            """,
            v2 = """
                public class Source {
                    @Nullable public String nonFinalField = null;
                    @Nullable public final String finalField = null;
                    @Nullable public String foo() { return "bar"; }
                }
            """
        ) {
            assertHasErrors(
                "Field nonFinalField: Nullability breaking change.",
                "Field finalField: From non-nullable to nullable breaking change.",
                "Method com.example.Source.foo(): From non-null returning to null returning breaking change."
            )
            assertHasNoWarning()
            assertHasNoInformation()
        }
    }

    @Test
    fun `from non-null returning to null returning is breaking (kotlin)`() {

        checkNotBinaryCompatibleKotlin(
            v1 = """
                class Source {
                    val someVal: String = "some"
                    var someVar: String = "some"
                    fun foo(): String = "bar"
                }
            """,
            v2 = """
                class Source {
                    val someVal: String? = null
                    var someVar: String? = null
                    fun foo(): String? = null
                }
            """
        ) {
            assertHasErrors(
                "Method com.example.Source.foo(): From non-null returning to null returning breaking change.",
                "Method com.example.Source.getSomeVal(): From non-null returning to null returning breaking change.",
                "Method com.example.Source.getSomeVar(): From non-null returning to null returning breaking change."
            )
            assertHasWarnings(
                "Method com.example.Source.setSomeVar(java.lang.String): Parameter 0 nullability changed from non-nullable to nullable"
            )
            assertHasNoInformation()
        }
    }

    @Test
    fun `from null accepting to non-null accepting is breaking (java)`() {

        checkNotBinaryCompatibleJava(
            v1 = """
                public class Source {
                    public Source(@Nullable String some) {}
                    @Nullable public String nonFinalField = null;
                    public String foo(@Nullable String bar) { return "some"; }
                }
            """,
            v2 = """
                public class Source {
                    public Source(String some) {}
                    public String nonFinalField = "some";
                    public String foo(String bar) { return bar; }
                }
            """
        ) {
            assertHasErrors(
                "Field nonFinalField: Nullability breaking change.",
                "Method com.example.Source.foo(java.lang.String): Parameter 0 from null accepting to non-null accepting breaking change.",
                "Constructor com.example.Source(java.lang.String): Parameter 0 from null accepting to non-null accepting breaking change."
            )
            assertHasNoWarning()
            assertHasNoInformation()
        }
    }

    @Test
    fun `from null accepting to non-null accepting is breaking (kotlin)`() {

        checkNotBinaryCompatibleKotlin(
            v1 = """
                class Source(some: String?) {
                    var someVar: String? = null
                    fun foo(bar: String?) {}
                }
            """,
            v2 = """
                class Source(some: String) {
                    var someVar: String = "some"
                    fun foo(bar: String) {}
                }
            """
        ) {
            assertHasErrors(
                "Method com.example.Source.foo(java.lang.String): Parameter 0 from null accepting to non-null accepting breaking change.",
                "Method com.example.Source.setSomeVar(java.lang.String): Parameter 0 from null accepting to non-null accepting breaking change.",
                "Constructor com.example.Source(java.lang.String): Parameter 0 from null accepting to non-null accepting breaking change."
            )
            assertHasWarnings(
                "Method com.example.Source.getSomeVar(): Return nullability changed from nullable to non-nullable"
            )
            assertHasNoInformation()
        }
    }

    @Test
    fun `from null returning to non-null returning is not breaking (java)`() {

        checkBinaryCompatibleJava(
            v1 = """
                public class Source {
                    @Nullable public final String finalField = null;
                    @Nullable public String foo(String bar) { return bar; }
                }
            """,
            v2 = """
                public class Source {
                    public final String finalField = null;
                    public String foo(String bar) { return bar; }
                }
            """
        ) {
            assertHasNoError()
            assertHasWarnings(
                "Field finalField: Nullability changed from nullable to non-nullable",
                "Method com.example.Source.foo(java.lang.String): Return nullability changed from nullable to non-nullable"
            )
            assertHasNoInformation()
        }
    }

    @Test
    fun `from null returning to non-null returning is not breaking (kotlin)`() {

        checkBinaryCompatibleKotlin(
            v1 = """
                class Source {
                    val someVal: String? = null
                    fun foo(): String? = null
                }
            """,
            v2 = """
                class Source {
                    val someVal: String = "some"
                    fun foo(): String = "bar"
                }
            """
        ) {
            assertHasNoError()
            assertHasWarnings(
                "Method com.example.Source.foo(): Return nullability changed from nullable to non-nullable",
                "Method com.example.Source.getSomeVal(): Return nullability changed from nullable to non-nullable"
            )
            assertHasNoInformation()
        }
    }

    @Test
    fun `from non-null accepting to null accepting is not breaking (java)`() {

        checkBinaryCompatibleJava(
            v1 = """
                public class Source {
                    public Source(String some) {}
                    public String foo(String bar) { return bar; }
                }
            """,
            v2 = """
                public class Source {
                    public Source(@Nullable String some) {}
                    public String foo(@Nullable String bar) { return "some"; }
                }
            """
        ) {
            assertHasNoError()
            assertHasWarnings(
                "Method com.example.Source.foo(java.lang.String): Parameter 0 nullability changed from non-nullable to nullable",
                "Constructor com.example.Source(java.lang.String): Parameter 0 nullability changed from non-nullable to nullable"
            )
            assertHasNoInformation()
        }
    }

    @Test
    fun `from non-null accepting to null accepting is not breaking (kotlin)`() {

        checkBinaryCompatibleKotlin(
            v1 = """
                class Source(some: String) {
                    fun foo(bar: String) {}
                }
                operator fun Source.invoke(arg: String) {}
            """,
            v2 = """
                class Source(some: String?) {
                    fun foo(bar: String?) {}
                }
                operator fun Source.invoke(arg: String?) {}
            """
        ) {
            assertHasNoError()
            assertHasWarnings(
                "Method com.example.Source.foo(java.lang.String): Parameter 0 nullability changed from non-nullable to nullable",
                "Constructor com.example.Source(java.lang.String): Parameter 0 nullability changed from non-nullable to nullable",
                "Method com.example.SourceKt.invoke(com.example.Source,java.lang.String): Parameter 1 nullability changed from non-nullable to nullable"
            )
            assertHasNoInformation()
        }
    }
}
