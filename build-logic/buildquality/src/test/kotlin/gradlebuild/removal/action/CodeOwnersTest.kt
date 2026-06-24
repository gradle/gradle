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

package gradlebuild.removal.action

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class CodeOwnersTest {

    private val codeOwners = CodeOwners.parse(
        listOf(
            "# a comment",
            "",
            "platforms/core-configuration/               @gradle/bt-cortex",
            "platforms/core-configuration/kotlin-dsl/    @gradle/bt-tide",
            "platforms/jvm/                              @gradle/bt-jvm",
            "platforms/jvm/testing-jvm/                  @gradle/bt-jvm @gradle/dv-efficiency-team",
            "/build-logic/                              @gradle/bt-developer-productivity",
            "subprojects/core                           # @gradle/bt-cortex owner commented out",
            "*.md                                        @gradle/bt-docs-reviewers @somebody",
        )
    )

    @Test
    fun `last matching rule wins`() {
        assertEquals(listOf("bt-cortex"), codeOwners.teamsFor("platforms/core-configuration/model-core/src/main/java/Foo.java"))
        assertEquals(listOf("bt-tide"), codeOwners.teamsFor("platforms/core-configuration/kotlin-dsl/src/main/kotlin/Bar.kt"))
    }

    @Test
    fun `directory prefix does not match a sibling with a longer name`() {
        // testing-jvm/ must not own testing-jvm-infrastructure; it falls back to the platforms/jvm/ rule.
        assertEquals(listOf("bt-jvm"), codeOwners.teamsFor("platforms/jvm/testing-jvm-infrastructure/src/main/java/X.java"))
        assertEquals(listOf("bt-jvm", "dv-efficiency-team"), codeOwners.teamsFor("platforms/jvm/testing-jvm/src/main/java/Y.java"))
    }

    @Test
    fun `leading slash anchors to the repository root`() {
        assertEquals(listOf("bt-developer-productivity"), codeOwners.teamsFor("build-logic/src/main/kotlin/X.kt"))
        // An anchored rule must not match the same name nested deeper.
        assertEquals(emptyList<String>(), codeOwners.teamsFor("platforms/x/build-logic/Y.kt"))
    }

    @Test
    fun `commented-out owner leaves the path unowned`() {
        assertEquals(emptyList<String>(), codeOwners.teamsFor("subprojects/core/src/main/java/DefaultProject.java"))
    }

    @Test
    fun `individual user owners are ignored, only org teams kept`() {
        assertEquals(listOf("bt-docs-reviewers"), codeOwners.teamsFor("README.md"))
    }

    @Test
    fun `unmatched path is unowned`() {
        assertEquals(emptyList<String>(), codeOwners.teamsFor("some/unrelated/path.txt"))
    }
}
