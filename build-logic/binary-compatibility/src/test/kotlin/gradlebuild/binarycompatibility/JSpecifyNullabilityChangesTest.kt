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

class JSpecifyNullabilityChangesTest : AbstractJavaNullabilityChangesTest() {

    override val nullableAnnotationName: String = "org.jspecify.annotations.Nullable"

    @Test
    fun `from non-null array returning to null returning is breaking`() {

        checkNotBinaryCompatibleJava(
            v1 = """
                import java.util.List;
                public class Source {
                    public String[] nonFinalField = new String[] {"some"};
                    public final String[] finalField = new String[] {"some"};
                    public String[] foo() { return new String[] {"some"}; }
                }
            """,
            v2 = """
                import java.util.Arrays;
                import java.util.List;
                import $nullableAnnotationName;
                public class Source {
                    public String @Nullable [] nonFinalField = new String[] {"some", null};
                    public final String @Nullable [] finalField = new String[] {"some", null};
                    public String @Nullable [] foo() { return new String[] {"some", null}; }
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
    fun `from non-null array element returning to null returning is breaking`() {

        checkNotBinaryCompatibleJava(
            v1 = """
                import java.util.List;
                public class Source {
                    public String[] nonFinalField = new String[] {"some"};
                    public final String[] finalField = new String[] {"some"};
                    public String[] foo() { return new String[] {"some"}; }
                }
            """,
            v2 = """
                import java.util.Arrays;
                import java.util.List;
                import $nullableAnnotationName;
                public class Source {
                    public @Nullable String[] nonFinalField = new String[] {"some", null};
                    public final @Nullable String[] finalField = new String[] {"some", null};
                    public @Nullable String[] foo() { return new String[] {"some", null}; }
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
    fun `from non-null generic type argument returning to null returning is breaking`() {

        checkNotBinaryCompatibleJava(
            v1 = """
                import java.util.List;
                public class Source {
                    public List<String> nonFinalField = List.of("some");
                    public final List<String> finalField = List.of("some");
                    public List<String> foo() { return List.of("some"); }
                }
            """,
            v2 = """
                import java.util.Arrays;
                import java.util.List;
                import $nullableAnnotationName;
                public class Source {
                    public List<@Nullable String> nonFinalField = Arrays.asList("some", null);
                    public final List<@Nullable String> finalField = Arrays.asList("some", null);
                    public List<@Nullable String> foo() { return Arrays.asList("some", null); }
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
    fun `from null array accepting to non-null accepting is breaking`() {

        checkNotBinaryCompatibleJava(
            v1 = """
                import $nullableAnnotationName;
                public class Source {
                    public Source(String @Nullable [] some) {}
                    public String @Nullable [] nonFinalField = null;
                    public String foo(String @Nullable [] bar) { return "some"; }
                }
            """,
            v2 = """
                public class Source {
                    public Source(String[] some) {}
                    public String[] nonFinalField = new String[] {"some"};
                    public String foo(String[] bar) { return "some"; }
                }
            """
        ) {
            assertHasErrors(
                "Field nonFinalField: Nullability breaking change.",
                "Method com.example.Source.foo(java.lang.String[]): Parameter 0 from null accepting to non-null accepting breaking change.",
                "Constructor com.example.Source(java.lang.String[]): Parameter 0 from null accepting to non-null accepting breaking change."
            )
            assertHasNoWarning()
            assertHasNoInformation()
        }
    }

    @Test
    fun `from null array element accepting to non-null accepting is breaking`() {

        checkNotBinaryCompatibleJava(
            v1 = """
                import $nullableAnnotationName;
                public class Source {
                    public Source(@Nullable String[] some) {}
                    public @Nullable String[] nonFinalField = null;
                    public String foo(@Nullable String[] bar) { return "some"; }
                }
            """,
            v2 = """
                public class Source {
                    public Source(String[] some) {}
                    public String[] nonFinalField = new String[] {"some"};
                    public String foo(String[] bar) { return "some"; }
                }
            """
        ) {
            assertHasErrors(
                "Field nonFinalField: Nullability breaking change.",
                "Method com.example.Source.foo(java.lang.String[]): Parameter 0 from null accepting to non-null accepting breaking change.",
                "Constructor com.example.Source(java.lang.String[]): Parameter 0 from null accepting to non-null accepting breaking change."
            )
            assertHasNoWarning()
            assertHasNoInformation()
        }
    }

    @Test
    fun `from null generic type argument accepting to non-null accepting is breaking`() {

        checkNotBinaryCompatibleJava(
            v1 = """
                import java.util.Arrays;
                import java.util.List;
                import $nullableAnnotationName;
                public class Source {
                    public Source(List<@Nullable String> some) {}
                    public List<@Nullable String> nonFinalField = Arrays.asList("some", null);
                    public String foo(List<@Nullable String> bar) { return "some"; }
                }
            """,
            v2 = """
                import java.util.Arrays;
                import java.util.List;
                public class Source {
                    public Source(List<String> some) {}
                    public List<String> nonFinalField = List.of("some");
                    public String foo(List<String> bar) { return "some"; }
                }
            """
        ) {
            assertHasErrors(
                "Field nonFinalField: Nullability breaking change.",
                "Method com.example.Source.foo(java.util.List): Parameter 0 from null accepting to non-null accepting breaking change.",
                "Constructor com.example.Source(java.util.List): Parameter 0 from null accepting to non-null accepting breaking change."
            )
            assertHasNoWarning()
            assertHasNoInformation()
        }
    }

    @Test
    fun `from null array returning to non-null returning is not breaking`() {

        checkBinaryCompatibleJava(
            v1 = """
                import $nullableAnnotationName;
                public class Source {
                    public final String @Nullable [] finalField = null;
                    public String @Nullable [] foo(String bar) { return null; }
                }
            """,
            v2 = """
                public class Source {
                    public final String[] finalField = new String[] {"bar"};
                    public String[] foo(String bar) { return new String[] {bar}; }
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
    fun `from null array element returning to non-null returning is not breaking`() {

        checkBinaryCompatibleJava(
            v1 = """
                import $nullableAnnotationName;
                public class Source {
                    public final @Nullable String[] finalField = new String[] {"some", null};
                    public @Nullable String[] foo(String bar) { return new String[] {bar, null}; }
                }
            """,
            v2 = """
                public class Source {
                    public final String[] finalField = new String[] {"bar"};
                    public String[] foo(String bar) { return new String[] {bar}; }
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
    fun `from null generic type argument returning to non-null returning is not breaking`() {

        checkBinaryCompatibleJava(
            v1 = """
                import java.util.List;
                import java.util.Arrays;
                import $nullableAnnotationName;
                public class Source {
                    public final List<@Nullable String> finalField = Arrays.asList("some", null);
                    public List<@Nullable String> foo(String bar) { return Arrays.asList(bar, null); }
                }
            """,
            v2 = """
                import java.util.List;
                public class Source {
                    public final List<String> finalField = List.of("some");
                    public List<String> foo(String bar) { return List.of(bar); }
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
    fun `from non-null array accepting to null accepting is not breaking`() {

        checkBinaryCompatibleJava(
            v1 = """
                public class Source {
                    public Source(String[] some) {}
                    public String foo(String[] bar) { return "some"; }
                }
            """,
            v2 = """
                import $nullableAnnotationName;
                public class Source {
                    public Source(String @Nullable [] some) {}
                    public String foo(String @Nullable [] bar) { return "some"; }
                }
            """
        ) {
            assertHasNoError()
            assertHasWarnings(
                "Method com.example.Source.foo(java.lang.String[]): Parameter 0 nullability changed from non-nullable to nullable",
                "Constructor com.example.Source(java.lang.String[]): Parameter 0 nullability changed from non-nullable to nullable"
            )
            assertHasNoInformation()
        }
    }

    @Test
    fun `from non-null array element accepting to null accepting is not breaking`() {

        checkBinaryCompatibleJava(
            v1 = """
                public class Source {
                    public Source(String[] some) {}
                    public String foo(String[] bar) { return "some"; }
                }
            """,
            v2 = """
                import $nullableAnnotationName;
                public class Source {
                    public Source(@Nullable String[] some) {}
                    public String foo(@Nullable String[] bar) { return "some"; }
                }
            """
        ) {
            assertHasNoError()
            assertHasWarnings(
                "Method com.example.Source.foo(java.lang.String[]): Parameter 0 nullability changed from non-nullable to nullable",
                "Constructor com.example.Source(java.lang.String[]): Parameter 0 nullability changed from non-nullable to nullable"
            )
            assertHasNoInformation()
        }
    }

    @Test
    fun `from non-null generic type argument accepting to null accepting is not breaking`() {

        checkBinaryCompatibleJava(
            v1 = """
                import java.util.List;
                public class Source {
                    public Source(List<String> some) {}
                    public String foo(List<String> bar) { return "some"; }
                }
            """,
            v2 = """
                import java.util.List;
                import $nullableAnnotationName;
                public class Source {
                    public Source(List<@Nullable String> some) {}
                    public String foo(List<@Nullable String> bar) { return "some"; }
                }
            """
        ) {
            assertHasNoError()
            assertHasWarnings(
                "Method com.example.Source.foo(java.util.List): Parameter 0 nullability changed from non-nullable to nullable",
                "Constructor com.example.Source(java.util.List): Parameter 0 nullability changed from non-nullable to nullable"
            )
            assertHasNoInformation()
        }
    }

    @Test
    fun `from null class type argument to non-null might be breaking`() {

        checkNotBinaryCompatibleJava(
            v1 = """
                import $nullableAnnotationName;
                public class Source<String, @Nullable T, Integer> {}
            """,
            v2 = """
                public class Source<String, T, Integer> {}
            """
        ) {
            assertHasErrors(
                "Class com.example.Source: Type parameter 1 nullability changed, might be a breaking change depending on its usage.",
            )
            assertHasNoWarning()
            assertHasNoInformation()
        }
    }

    @Test
    fun `from null class type argument bound to non-null might be breaking`() {

        checkNotBinaryCompatibleJava(
            v1 = """
                import java.util.List;
                import $nullableAnnotationName;
                public class Source<String, T extends List<@Nullable CharSequence>, Integer> {}
            """,
            v2 = """
                import java.util.List;
                public class Source<String, T extends List<CharSequence>, Integer> {}
            """
        ) {
            assertHasErrors(
                "Class com.example.Source: Type parameter 1 nullability changed, might be a breaking change depending on its usage.",
            )
            assertHasNoWarning()
            assertHasNoInformation()
        }
    }

    @Test
    fun `from nonnull class type argument to null might be breaking`() {

        checkNotBinaryCompatibleJava(
            v1 = """
                public class Source<String, T, Integer> {}
            """,
            v2 = """
                import $nullableAnnotationName;
                public class Source<String, @Nullable T, Integer> {}
            """
        ) {
            assertHasErrors(
                "Class com.example.Source: Type parameter 1 nullability changed, might be a breaking change depending on its usage.",
            )
            assertHasNoWarning()
            assertHasNoInformation()
        }
    }

    @Test
    fun `from non-null class type argument bound to null might be breaking`() {

        checkNotBinaryCompatibleJava(
            v1 = """
                import java.util.List;
                public class Source<String, T extends List<CharSequence>, Integer> {}
            """,
            v2 = """
                import java.util.List;
                import $nullableAnnotationName;
                public class Source<String, T extends List<@Nullable CharSequence>, Integer> {}
            """
        ) {
            assertHasErrors(
                "Class com.example.Source: Type parameter 1 nullability changed, might be a breaking change depending on its usage.",
            )
            assertHasNoWarning()
            assertHasNoInformation()
        }
    }
}
