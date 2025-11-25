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

package org.gradle.internal.cc.impl.isolated

class IsolatedProjectsExtensionsAccessIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "mutation of the parent extension container is prohibited"() {
        settingsFile """
            include(":other")
        """
        buildFile("other/build.gradle", """
            import static org.gradle.api.reflect.TypeOf.typeOf

            rootProject.extensions.$mutation
        """)

        when:
        isolatedProjectsFails "help"

        then:
        failureCauseContains("Mutation of the isolated extensions is prohibited")

        where:
        mutation << [
            "add(Integer.class, 'foo', 42)",
            "add(typeOf(Integer.class),'foo', 42)",
            "add('foo', 42)",
            // create
            // configure
        ]
    }

    def "access an extension of a sibling project is prohibited"() {
        settingsFile """
            include(":a")
            include(":b")
        """
        file("buildSrc/src/main/java/com/example/Foo.java") << """
            package com.example;
            import java.util.List;

            public interface Foo {
                public int getValue();
                public void setValue(int value);
            }
        """
        buildFile("a/build.gradle", """
            import com.example.Foo
            def foo = extensions.create("foo", Foo.class)
            foo.value = 42
        """)

        buildFile("b/build.gradle", """
            import com.example.Foo
            def foo = project(":a").extensions.getByName("foo")
            println(foo.value)
        """)

        when:
        isolatedProjectsFails "help"

        then:
        failureCauseContains("Project ':b' cannot access 'Project.extensions' functionality on another project ':a'")
    }

    def "project's extensions are isolated"() {
        settingsFile """
            include(":a")
            include(":b")
        """
        file("buildSrc/src/main/java/com/example/Foo.java") << """
            package com.example;
            import java.util.List;

            public interface Foo {
                public List<Integer> getValue();
                public void setValue(List<Integer> value);
            }
        """
        buildFile """
            import com.example.Foo

            def foo = extensions.create("foo", Foo.class)
            foo.value = [42]
        """
        buildFile("a/build.gradle", """
            import com.example.Foo
            import static org.gradle.api.reflect.TypeOf.typeOf

            def foo = rootProject.extensions.$extensionAccess
            foo.value.add(0)
            println("Project ':a' foo = \${foo.value}")
        """)
        buildFile("b/build.gradle", """
            import com.example.Foo
            import static org.gradle.api.reflect.TypeOf.typeOf

            def foo = rootProject.extensions.$extensionAccess
            foo.value.add(1)
            println("Project ':b' foo = \${foo.value}")
        """)

        when:
        isolatedProjectsRun "help"

        then:
        outputContains("Project ':a' foo = [42, 0]")
        outputContains("Project ':b' foo = [42, 1]")

        where:
        extensionAccess << [
            "findByType(Foo.class)",
            "findByType(typeOf(Foo.class))",
            "findByName('foo')",
            "getByType(Foo.class)",
            "getByType(typeOf(Foo.class))",
            "getByName('foo')"
        ]
    }

    def "project's extra properties are isolated"() {
        settingsFile """
            include(":a")
            include(":b")
        """
        buildFile """
            extensions.extraProperties.set("numbers", [42])
        """
        buildFile("a/build.gradle", """
            def numbers = rootProject.extensions.extraProperties.get("numbers")
            numbers.add(0)
            println("Project ':a' numbers = \${numbers.value}")
        """)
        buildFile("b/build.gradle", """
            def numbers = rootProject.extensions.extraProperties.get("numbers")
            numbers.add(1)
            println("Project ':b' numbers = \${numbers.value}")
        """)

        when:
        isolatedProjectsRun "help"

        then:
        outputContains("Project ':a' numbers = [42, 0]")
        outputContains("Project ':b' numbers = [42, 1]")
    }

    def "access to non-serializable extensions emits a problem"() {
        settingsFile """
            include(":a")
        """
        buildFile """
            extensions.add("number", 42)
            extensions.add("broken", this)
        """
        buildFile("a/build.gradle", """
            println("Number is \${rootProject.extensions.getByName('number')}")
            println("Broken is \${rootProject.extensions.getByName('broken')}")
        """)

        when:
        isolatedProjectsFails "help"

        then:
        outputContains("Number is 42")

        and:
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'a/build.gradle': line 2: Extension with name 'broken' of project ':' cannot be serialized")
        }
    }

    def "project's extensions isolated on the first access and cannot be changed after"() {
        settingsFile """
            include(":a")
            include(":a:b")
        """
        buildFile """
            import java.util.concurrent.atomic.AtomicReference
            AtomicReference<String> value = new AtomicReference("before")
            configurations {
                consumable("foo") {
                    value.set("after")
                    println("Setting value to after")
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, named(Category, "foo"))
                    }
                }
            }
            extensions.add("broken", value)
        """
        buildFile("a/build.gradle", """
            println("Broken is \${rootProject.extensions.getByName('broken')}")
            def deps = configurations.dependencyScope("deps") {
                dependencies.add(dependencyFactory.create(rootProject))
            }
            def res = configurations.resolvable("res") {
                extendsFrom(deps.get())
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, named(Category, "foo"))
                }
            }
            res.get().getFiles() // Realize a dependency on the parent project at configuration time
        """)
        buildFile("a/b/build.gradle", """
            println("Broken is \${rootProject.extensions.getByName('broken')}")
        """)

        when:
        isolatedProjectsRun "help"

        then:
        outputContains("Broken is before")
        outputContains("Broken is before")
    }

    //TODO Test isolation of ExtensionContainer.getExtensionSchema?
    //TODO Test access to ExtensionContainer.getAsMap?
    //TODO Test access to ExtensionContainer.getHoldersAsMap?
}
