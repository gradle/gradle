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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File


class RemovalReportWorkActionTest {

    @TempDir
    lateinit var tempDir: File

    // The synthetic sources below use the Gradle 10 marker names, so the scanner is configured for target 10.
    private val markers = RemovalTimeline.markersByMethod(10)

    private fun java(name: String, @Language("java") body: String): List<RemovalFinding> {
        val file = File(tempDir, "$name.java").apply { writeText(body) }
        return JavaRemovalCollector(tempDir.toPath(), markers).collectFrom(file)
    }

    private fun kotlin(name: String, body: String): List<RemovalFinding> {
        val file = File(tempDir, "$name.kt").apply { writeText(body) }
        return KotlinRemovalCollector(tempDir.toPath(), markers).collectFrom(file)
    }

    @Test
    fun `next major version is derived from the current version`() {
        assertEquals(10, nextMajorGradleVersion("9.7.0"))
        assertEquals(10, nextMajorGradleVersion("9.0-milestone-5\n"))
        assertEquals(11, nextMajorGradleVersion("10.2.0"))
    }

    @Test
    fun `markers track the target major version`() {
        assertEquals(RemovalTimeline.REMOVED, RemovalTimeline.markersByMethod(10)["willBeRemovedInGradle10"])
        assertEquals(RemovalTimeline.REMOVED, RemovalTimeline.markersByMethod(11)["willBeRemovedInGradle11"])
        // startingWithGradle11 is "this major" for target 11 but "next major" for target 10.
        assertEquals(RemovalTimeline.STARTING_NEXT_MAJOR, RemovalTimeline.markersByMethod(10)["startingWithGradle11"])
        assertEquals(RemovalTimeline.STARTING_THIS_MAJOR, RemovalTimeline.markersByMethod(11)["startingWithGradle11"])
        // The next-major marker is version-independent.
        assertEquals(RemovalTimeline.ERROR_NEXT_MAJOR, RemovalTimeline.markersByMethod(10)["willBecomeAnErrorInNextMajorGradleVersion"])
    }

    @Test
    fun `simple inline removal chain`() {
        val findings = java(
            "A",
            """
            package p;
            class A {
                void m() {
                    DeprecationLogger.deprecateMethod(Foo.class, "bar()").willBeRemovedInGradle10().withUpgradeGuideSection(9, "sec").nagUser();
                }
            }
            """.trimIndent()
        )

        assertEquals(1, findings.size)
        val f = findings.single()
        assertEquals(RemovalTimeline.REMOVED, f.timeline)
        assertEquals(RemovalKind.METHOD, f.kind)
        assertEquals("Foo.bar()", f.symbol)
        assertEquals(9, f.guideMajor)
        assertEquals("sec", f.guideSection)
    }

    @Test
    fun `multi-line chain with advice and upgrade guide`() {
        val findings = java(
            "B",
            """
            package p;
            class B {
                void m() {
                    DeprecationLogger.deprecateProperty(Bar.class, "baz")
                        .withAdvice("Use qux instead.")
                        .willBecomeAnErrorInGradle10()
                        .withUpgradeGuideSection(8, "baz_section")
                        .nagUser();
                }
            }
            """.trimIndent()
        )

        val f = findings.single()
        assertEquals(RemovalTimeline.ERROR_THIS_MAJOR, f.timeline)
        assertEquals(RemovalKind.PROPERTY, f.kind)
        assertEquals("Bar.baz", f.symbol)
        assertEquals(8, f.guideMajor)
        assertEquals("baz_section", f.guideSection)
    }

    @Test
    fun `variable-assigned builder intermediate yields dynamic`() {
        val findings = java(
            "C",
            """
            package p;
            class C {
                void m() {
                    Builder d = DeprecationLogger.deprecateAction("Doing X");
                    d.withAdvice("a").willBecomeAnErrorInGradle10().undocumented().nagUser();
                }
            }
            """.trimIndent()
        )

        val f = findings.single()
        assertEquals(RemovalTimeline.ERROR_THIS_MAJOR, f.timeline)
        assertEquals(RemovalKind.OTHER, f.kind)
        assertTrue(f.symbol.startsWith("<dynamic"), "expected dynamic symbol but was ${f.symbol}")
        assertNull(f.guideSection)
    }

    @Test
    fun `lambda-enclosed chain with dynamic argument`() {
        val findings = java(
            "D",
            """
            package p;
            class D {
                void m() {
                    register(task -> DeprecationLogger.deprecateTask(task.getName()).willBeRemovedInGradle10().withUpgradeGuideSection(9, "ide_task_deprecation").nagUser());
                }
            }
            """.trimIndent()
        )

        val f = findings.single()
        assertEquals(RemovalKind.TASK, f.kind)
        assertTrue(f.symbol.startsWith("<dynamic"), "expected dynamic symbol but was ${f.symbol}")
        assertEquals("ide_task_deprecation", f.guideSection)
    }

    @Test
    fun `startingWith message argument is not treated as the symbol`() {
        val findings = java(
            "E",
            """
            package p;
            class E {
                void m() {
                    DeprecationLogger.deprecateBehaviour("Some behaviour.")
                        .startingWithGradle10("this property will no longer be treated like a property")
                        .withUpgradeGuideSection(8, "groovy_boolean_properties")
                        .nagUser();
                }
            }
            """.trimIndent()
        )

        val f = findings.single()
        assertEquals(RemovalTimeline.STARTING_THIS_MAJOR, f.timeline)
        assertEquals(RemovalKind.BEHAVIOUR, f.kind)
        assertEquals("Some behaviour.", f.symbol)
        assertEquals("groovy_boolean_properties", f.guideSection)
    }

    @Test
    fun `commented-out chain yields no findings`() {
        val findings = java(
            "F",
            """
            package p;
            class F {
                void m() {
                    // DeprecationLogger.deprecateMethod(Foo.class, "old()").willBeRemovedInGradle10().nagUser();
                    int x = 1;
                }
            }
            """.trimIndent()
        )

        assertTrue(findings.isEmpty(), "expected no findings but was $findings")
    }

    @Test
    fun `kotlin deprecateType with class literal`() {
        val findings = kotlin(
            "K",
            """
            package p
            class K {
                fun m() {
                    DeprecationLogger.deprecateType(MyDelegate::class.java)
                        .willBeRemovedInGradle10()
                        .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
                        .nagUser()
                }
            }
            """.trimIndent()
        )

        val f = findings.single()
        assertEquals(RemovalTimeline.REMOVED, f.timeline)
        assertEquals(RemovalKind.TYPE, f.kind)
        assertEquals("MyDelegate", f.symbol)
        assertEquals(9, f.guideMajor)
        assertEquals("kotlin_dsl_delegated_properties", f.guideSection)
    }
}


/**
 * Local stand-in for IntelliJ's `org.intellij.lang.annotations.Language` so the test source above can
 * be annotated for readability without adding a dependency. No runtime effect.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
private annotation class Language(val value: String)
