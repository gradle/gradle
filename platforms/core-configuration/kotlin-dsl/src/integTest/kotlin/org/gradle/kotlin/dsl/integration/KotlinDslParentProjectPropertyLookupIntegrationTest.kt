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

package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


/**
 * Verifies that resolving a `val ... by project` delegated property from the parent project
 * adds the Kotlin delegation caller context to the parent-project lookup deprecation. The
 * deprecation action stays stable; the caller detail is surfaced in the context.
 *
 * @see org.gradle.kotlin.dsl.PropertyDelegate
 */
@Requires(
    TestExecutionPreconditions.NotIsolatedProjects::class,
    reason = "Under Isolated Projects, parent-project lookup is disabled entirely; no deprecation fires"
)
class KotlinDslParentProjectPropertyLookupIntegrationTest : AbstractKotlinIntegrationTest() {

    private
    val commonUpgradePart =
        "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#"

    private
    fun expectParentLookupDeprecation(propertyName: String) {
        executer.expectDocumentedDeprecationWarning(
            "Implicit lookup of properties in parent projects has been deprecated. " +
                "This will fail with an error in Gradle 10. " +
                "Property '$propertyName' was not declared in project ':a' and was resolved from root project 'root'. " +
                "This lookup was initiated by 'val ... by project'. " +
                "${commonUpgradePart}deprecated_implicit_lookup_in_parent_projects"
        )
    }

    @Test
    fun `val by project resolved from parent is deprecated with the Kotlin delegation caller context`() {
        withSettings(
            """
            rootProject.name = "root"
            include("a")
            """
        )
        withBuildScript(
            """
            extra["foo"] = "fromRoot"
            extra["bar"] = "alsoFromRoot"
            """
        )
        withFile(
            "a/build.gradle.kts",
            """
            @file:Suppress("DEPRECATION")

            val foo: String by project
            val bar: String? by project

            println("foo=" + foo)
            println("bar=" + bar)
            """
        )

        // The `by project` syntax itself is independently deprecated.
        executer.expectDocumentedDeprecationWarning(
            "The 'val name: Type by project' property delegate syntax has been deprecated. " +
                "This is scheduled to be removed in Gradle 10. " +
                "Use 'val property = project.property(name)' instead. " +
                "${commonUpgradePart}kotlin_dsl_delegated_properties"
        )
        executer.expectDocumentedDeprecationWarning(
            "The 'val name: Type? by project' property delegate syntax has been deprecated. " +
                "This is scheduled to be removed in Gradle 10. " +
                "Use 'val property = project.findProperty(name)' instead. " +
                "${commonUpgradePart}kotlin_dsl_delegated_properties"
        )
        // The parent walk is annotated with the Kotlin-delegation caller context.
        expectParentLookupDeprecation("foo")
        expectParentLookupDeprecation("bar")

        val result = build("help")

        assertThat(result.output, containsString("foo=fromRoot"))
        assertThat(result.output, containsString("bar=alsoFromRoot"))
    }
}
