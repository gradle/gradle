package org.gradle.script.lang.kotlin.support

import org.gradle.script.lang.kotlin.FolderBasedTest

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

class ProjectRootOfTest : FolderBasedTest() {

    @Test
    fun `given a script file under a nested project it should return the nested project root`() {

        withFolders {
            "root" {
                "nested-project-root" {
                    // a nested project is detected by the presence of a settings.gradle file
                    withFile("settings.gradle")
                    "sub-project" {
                        withFile("build.gradle.kts")
                    }
                }
            }
        }

        assertThat(
            projectRootOf(
                scriptFile = file("root/nested-project-root/sub-project/build.gradle.kts"),
                importedProjectRoot = folder("root")),
            equalTo(folder("root/nested-project-root")))
    }

    @Test
    fun `given a script file under a separate project it should return the separate project root`() {

        withFolders {
            "root" {
            }
            "separate-project-root" {
                withFile("build.gradle.kts")
            }
        }

        assertThat(
            projectRootOf(
                scriptFile = file("separate-project-root/build.gradle.kts"),
                importedProjectRoot = folder("root")),
            equalTo(folder("separate-project-root")))
    }

    @Test
    fun `given a script file under a separate nested project it should return the separate nested project root`() {

        withFolders {
            "root" {
            }
            "separate" {
                "nested-project-root" {
                    // a nested project is detected by the presence of a settings.gradle file
                    withFile("settings.gradle")
                    "sub-project" {
                        withFile("build.gradle.kts")
                    }
                }
            }
        }

        assertThat(
            projectRootOf(
                scriptFile = file("separate/nested-project-root/sub-project/build.gradle.kts"),
                importedProjectRoot = folder("root")),
            equalTo(folder("separate/nested-project-root")))
    }

    @Test
    fun `given a script file under the imported project it should return the imported project root`() {

        withFolders {
            "root" {
                "sub-project" {
                    withFile("build.gradle.kts")
                }
            }
        }

        assertThat(
            projectRootOf(
                scriptFile = file("root/sub-project/build.gradle.kts"),
                importedProjectRoot = folder("root")),
            equalTo(folder("root")))
    }
}
