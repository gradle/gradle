package org.gradle.kotlin.dsl.support

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import java.io.File
import java.util.jar.JarFile

class ImplicitImportsTest : AbstractIntegrationTest() {

    @Test
    fun `implicit imports are fully qualified to allow use of the preferred type amongst those with same simple name in different Gradle API packages`() {

        // given:
        existing("settings.gradle").appendText("""
            rootProject.name = "some-project"
        """)
        withBuildScript("""

            plugins {
                java
            }

            // Prefer org.gradle.api.tasks.bundling.Jar over org.gradle.jvm.tasks.Jar
            tasks.withType<Jar> {
                manifest {
                    attributes(mapOf("Foo" to "Bar"))
                }
            }

        """)

        // when:
        build("jar")

        // then:
        JarFile(File(projectRoot, "build/libs/some-project.jar")).use { jar ->
            assertThat(jar.manifest.mainAttributes.getValue("Foo"), equalTo("Bar"))
        }
    }
}
