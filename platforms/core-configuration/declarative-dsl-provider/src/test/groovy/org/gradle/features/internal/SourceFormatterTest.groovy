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

package org.gradle.features.internal

import org.gradle.features.internal.builders.SourceFormatter
import spock.lang.Specification

class SourceFormatterTest extends Specification {

    // --- Core indenting ---

    def "formats a basic class skeleton"() {
        given:
        def input = '''
            package x;

            import y.Z;

            public class Foo {
                void bar() {
                    baz();
                }
            }
        '''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
package x;

import y.Z;

public class Foo {
    void bar() {
        baz();
    }
}
'''
    }

    def "handles 3+ levels of nesting including }-only and };-style lines"() {
        given:
        def input = '''
class A {
    class B {
        class C {
            void f() {
                g();
            }
        };
    }
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
class A {
    class B {
        class C {
            void f() {
                g();
            }
        };
    }
}
'''
    }

    def "handles a single-line { } block as a no-op delta"() {
        given:
        def input = '''
class Foo {
    @Inject public Foo() { }

    void bar() {
        baz();
    }
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
class Foo {
    @Inject public Foo() { }

    void bar() {
        baz();
    }
}
'''
    }

    def "handles lambda body with mixed brace+paren close"() {
        given:
        def input = '''
class Foo {
    void bar() {
        getTaskRegistrar().register("name", task -> {
            task.doLast(t -> {
                System.out.println("hi");
            });
        });
    }
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
class Foo {
    void bar() {
        getTaskRegistrar().register("name", task -> {
            task.doLast(t -> {
                System.out.println("hi");
            });
        });
    }
}
'''
    }

    // --- Mixed leading-closer runs ---

    def "}) line dedents one brace level and one paren level"() {
        // Inside `register("foo", task -> { ... });` the closing line `});` first
        // dedents the lambda block (}) then closes the call paren ()).
        given:
        def input = '''
class Foo {
    void bar() {
        register("foo", task -> {
            doSomething();
        });
    }
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
class Foo {
    void bar() {
        register("foo", task -> {
            doSomething();
        });
    }
}
'''
    }

    def "}}) dedents two brace levels then one paren level"() {
        given:
        def input = '''
class Foo {
    void bar() {
        outer(arg, x -> {
            inner(y -> {
                doSomething();
            });
        });
    }
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        // Specifically verifying: the third-from-bottom line (closing the inner lambda
        // and its register-call paren) reads "        });" — at +2 indent inside the outer.
        formatted.contains("            });\n        });\n    }\n}")
    }

    def "lambda short-circuit: same-line ( and { do not double-indent the body"() {
        // The single-line `register("foo", task -> {` opens both '(' and '{'.
        // The lambda body should be at +1 indent (not +2), matching what the
        // multi-line equivalent below produces.
        given:
        def input = '''
class Foo {
    void bar() {
        register("name", task -> {
            doSomething();
        });
    }
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
class Foo {
    void bar() {
        register("name", task -> {
            doSomething();
        });
    }
}
'''
    }

    def "lambda on its own continuation line does indent the body one level deeper"() {
        // Here '(' is on one line and '{' is on a later line, so paren
        // continuation contributes to the lambda body's indent.
        given:
        def input = '''
class Foo {
    void bar() {
        register(
            "name",
            task -> {
                doSomething();
            }
        );
    }
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
class Foo {
    void bar() {
        register(
            "name",
            task -> {
                doSomething();
            }
        );
    }
}
'''
    }

    def ")); dedents two paren levels"() {
        // Each open paren contributes one continuation indent — so '42' is at +2
        // continuation past 'outer(inner(' — and the closing '));' line dedents
        // both paren levels in one walk.
        given:
        def input = '''
class Foo {
    void bar() {
        outer(inner(
            42
        ));
    }
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
class Foo {
    void bar() {
        outer(inner(
                42
        ));
    }
}
'''
    }

    def "}; behaves the same as } for indent purposes"() {
        given:
        def input = '''
class Outer {
    static abstract class Inner {
        abstract void f();
    };
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
class Outer {
    static abstract class Inner {
        abstract void f();
    };
}
'''
    }

    def "leading ).chained() line dedents and continues at parent indent"() {
        given:
        def input = '''
class Foo {
    void bar() {
        builder.start(
            "x"
        ).withFoo();
    }
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
class Foo {
    void bar() {
        builder.start(
            "x"
        ).withFoo();
    }
}
'''
    }

    def "real-shape ).withUnsafeDefinition().withUnsafeApplyAction(); from MultiTargetFeaturePluginBuilder"() {
        given:
        def input = '''
class Binding {
    void bind(Builder builder) {
        builder.bindFeature(
            "feat",
            FeatureDefinition.class,
            Target.class,
            FeatureImplPlugin.TargetApplyAction.class
        ).withUnsafeDefinition().withUnsafeApplyAction();
    }
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
class Binding {
    void bind(Builder builder) {
        builder.bindFeature(
            "feat",
            FeatureDefinition.class,
            Target.class,
            FeatureImplPlugin.TargetApplyAction.class
        ).withUnsafeDefinition().withUnsafeApplyAction();
    }
}
'''
    }

    // --- Continuation indenting ---

    def "multi-line method call indents arguments at +1 paren level"() {
        given:
        def input = '''
class Foo {
    void bar() {
        register(
            "name",
            42,
            true
        );
    }
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
class Foo {
    void bar() {
        register(
            "name",
            42,
            true
        );
    }
}
'''
    }

    def "closing line of form )modifiers; lands at parent indent"() {
        // `)${modifiers};` — paren close immediately followed by chained
        // calls then the statement terminator. Common shape from
        // MultiTargetFeaturePluginBuilder.
        given:
        def input = '''
class Binding {
    void bind(Builder builder) {
        builder.bind(
            "x",
            X.class
        ).withFoo();
    }
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted.contains("        ).withFoo();\n    }\n}")
    }

    // --- Whitespace rules ---

    def "annotation on its own line shares indent with the next declaration"() {
        given:
        def input = '''
class Foo {
    @Override
    public void bar() {
    }
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
class Foo {
    @Override
    public void bar() {
    }
}
'''
    }

    def "lines starting with semicolon or comma do not dedent"() {
        given:
        def input = '''
class Foo {
    int x = doSomething()
        ;
    void bar(int a
        , int b) {
    }
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        // The `;`-only line doesn't dedent (no leading closer); paren
        // continuation handles the comma line.
        formatted == '''\
class Foo {
    int x = doSomething()
    ;
    void bar(int a
        , int b) {
    }
}
'''
    }

    def "collapses runs of 3+ blank lines to a single blank line"() {
        given:
        def input = '''
class Foo {



    void bar() {
    }
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
class Foo {

    void bar() {
    }
}
'''
    }

    def "preserves a single intentional blank line as a separator"() {
        given:
        def input = '''
class Foo {
    int x;

    int y;
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
class Foo {
    int x;

    int y;
}
'''
    }

    def "strips leading blank lines"() {
        given:
        def input = "\n\n\npackage x;\n\nclass Foo {\n}\n"

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
package x;

class Foo {
}
'''
    }

    def "strips trailing whitespace from every line"() {
        given:
        def input = "class Foo {   \n    int x;  \t \n}  \n"

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
class Foo {
    int x;
}
'''
    }

    def "ensures exactly one trailing newline"() {
        given:
        def input = "class Foo {\n    int x;\n}\n\n\n\n"

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
class Foo {
    int x;
}
'''
        formatted.endsWith("\n")
        !formatted.endsWith("\n\n")
    }

    def "empty input returns empty string"() {
        expect:
        SourceFormatter.format("") == ""
        SourceFormatter.format(null) == ""
        SourceFormatter.format("\n\n\n") == ""
        SourceFormatter.format("   \n   \n") == ""
    }

    // --- Kotlin samples ---

    def "formats Kotlin class with override fun"() {
        given:
        def input = '''
package x

import org.gradle.api.Plugin
import org.gradle.api.Project

class Foo : Plugin<Project> {
    override fun apply(target: Project) {
        println("hi")
    }
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
package x

import org.gradle.api.Plugin
import org.gradle.api.Project

class Foo : Plugin<Project> {
    override fun apply(target: Project) {
        println("hi")
    }
}
'''
    }

    def "formats Kotlin lambda with parameter and nested lambda"() {
        given:
        def input = '''
class Foo {
    fun bar() {
        register("name") { task ->
            task.doLast { _ ->
                println("hi")
            }
        }
    }
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
class Foo {
    fun bar() {
        register("name") { task ->
            task.doLast { _ ->
                println("hi")
            }
        }
    }
}
'''
    }

    def "formats Kotlin annotated property"() {
        given:
        def input = '''
abstract class Foo {
    @get:Inject
    abstract val taskRegistrar: TaskRegistrar
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
abstract class Foo {
    @get:Inject
    abstract val taskRegistrar: TaskRegistrar
}
'''
    }

    // --- Defensive cases ---

    def "more closers than openers in a single line clamps without panicking"() {
        // Pathological line with too many closers; clamp protects the algorithm.
        given:
        def input = "class X {\n    void f() { } } } } } }\n}\n"

        when:
        def formatted = SourceFormatter.format(input)

        then:
        // The output may not be pretty, but it must not throw and must include all input chars.
        // Specifically the per-line clamp prevents a negative state from corrupting later lines.
        notThrown(IllegalStateException)
        formatted.contains("class X {")
        formatted.contains("void f()")
    }

    def "globally unbalanced input throws with a descriptive message"() {
        given:
        def input = "class Foo {\n    void bar() {\n        baz();\n}\n"  // missing one closing brace

        when:
        SourceFormatter.format(input)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("SourceFormatter: unbalanced output")
        e.message.contains("braces=")
    }

    def "Java annotation array literal stays on one line at parent indent"() {
        given:
        def input = '''
@RegistersProjectFeatures({ Foo.class, Bar.class })
class Settings {
}
'''

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
@RegistersProjectFeatures({ Foo.class, Bar.class })
class Settings {
}
'''
    }

    // --- Integration shape: prove the formatter handles the messy input we expect from
    //     the GString templates today. ---

    def "normalizes a messy GString-style template to clean output"() {
        // This input mimics what AbstractPluginBuilder.renderJava() produces today:
        // 12 spaces of leading indent on every line, plus a multi-line helper substitution
        // ("services") whose lines have a different (20-space) leading indent, plus a
        // trailing closing-brace at 12 spaces and a trailing whitespace-only line.
        given:
        def input = "\n            package x;\n\n            import y.Z;\n\n            public class Foo {\n\n                static class Inner {\n                    void f() {\n                        \n                    @Inject\n                    abstract Z getZ();\n                    }\n                }\n\n            }\n        "

        when:
        def formatted = SourceFormatter.format(input)

        then:
        formatted == '''\
package x;

import y.Z;

public class Foo {

    static class Inner {
        void f() {

            @Inject
            abstract Z getZ();
        }
    }

}
'''
    }
}
