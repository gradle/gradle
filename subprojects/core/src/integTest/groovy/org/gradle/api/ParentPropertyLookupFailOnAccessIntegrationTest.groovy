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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

/**
 * Tests for the {@code org.gradle.internal.fail-on-parent-property-lookup} internal flag,
 * which makes any implicit parent-project property/method lookup fail the build at the
 * lookup site with {@code InvalidUserCodeException}.
 */
class ParentPropertyLookupFailOnAccessIntegrationTest extends AbstractIntegrationSpec {

    private static final String FAIL_ON_PARENT_LOOKUP = "-Dorg.gradle.internal.fail-on-parent-property-lookup=true"

    def setup() {
        createDirs("child")
        settingsFile("""
            rootProject.name = "root"
            include "child"
        """)
        buildFile("""
            ext.foo = "bar"
            def fooMethod() { "from parent" }
        """)
    }

    @IgnoreIf({ GradleContextualExecuter.isolatedProjects })
    def "fails on #scenario from child"() {
        buildFile("child/build.gradle", "println $expression")

        when:
        fails "help", FAIL_ON_PARENT_LOOKUP

        then:
        failure.assertHasCause("Implicit parent-project property lookup is not allowed: property 'foo' was resolved from root project 'root' for project ':child'.")

        where:
        scenario             | expression
        "implicit reference" | "foo"
        "findProperty"       | "findProperty('foo')"
        "property"           | "property('foo')"
        "getProperty"        | "getProperty('foo')"
        "hasProperty"        | "hasProperty('foo')"
    }

    @IgnoreIf({ GradleContextualExecuter.isolatedProjects })
    def "fails on method invocation from child"() {
        buildFile("child/build.gradle", "println fooMethod()")

        when:
        fails "help", FAIL_ON_PARENT_LOOKUP

        then:
        failure.assertHasCause("Implicit parent-project method lookup is not allowed: method 'fooMethod' was resolved from root project 'root' for project ':child'.")
    }

    @IgnoreIf({ GradleContextualExecuter.isolatedProjects })
    def "succeeds when flag is not set"() {
        buildFile("child/build.gradle", """
            println foo
            println findProperty('foo')
            println property('foo')
            println getProperty('foo')
            println hasProperty('foo')
            println fooMethod()
        """)

        expect:
        succeeds "help"
    }

    def "succeeds when child defines the property locally even with flag set"() {
        buildFile("child/build.gradle", """
            ext.foo = "local"
            println foo
            println findProperty('foo')
        """)

        when:
        succeeds "help", FAIL_ON_PARENT_LOOKUP

        then:
        outputContains("local")
    }
}
