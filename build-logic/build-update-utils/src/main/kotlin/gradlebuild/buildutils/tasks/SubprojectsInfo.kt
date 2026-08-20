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

package gradlebuild.buildutils.tasks

import com.google.gson.GsonBuilder
import gradlebuild.buildutils.model.GradleSubproject
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault
import java.io.File
import kotlin.io.path.invariantSeparatorsPathString


@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
abstract class SubprojectsInfo : DefaultTask() {

    private
    val FLAKY_ANNOTATION = "org.gradle.test.fixtures.Flaky"

    private
    val rootPath = project.layout.projectDirectory.asFile.toPath()

    private
    val platformsFolder = project.layout.projectDirectory.dir("platforms")

    private
    val subprojectsFolder = project.layout.projectDirectory.dir("subprojects")

    private
    val testingFolder = project.layout.projectDirectory.dir("testing")

    private
    val packingFolder = project.layout.projectDirectory.dir("packaging")

    @get:Internal
    protected
    val subprojectsJson = project.layout.projectDirectory.file(".teamcity/subprojects.json")

    protected
    fun generateSubprojectsJson(): String {
        val subprojects = generateSubprojects()
        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(subprojects) + '\n'
    }

    private
    fun generateSubprojectsDirectories(): List<File> {
        val subprojectRoots = platformsFolder.asFile.listFiles(File::isDirectory)
            .plus(subprojectsFolder.asFile)
            .plus(testingFolder.asFile)
            .plus(packingFolder.asFile)

        return subprojectRoots.map { it.listFiles(File::isDirectory).asList() }.flatten()
    }

    private
    fun generateSubprojects(): List<GradleSubproject> {
        return generateSubprojectsDirectories()
            .filter {
                File(it, "build.gradle.kts").exists() ||
                    File(it, "build.gradle").exists()
            }
            .sortedBy { it.name }
            .map(this::generateSubproject)
    }


    private
    fun generateSubproject(subprojectDir: File): GradleSubproject {
        return GradleSubproject(
            subprojectDir.name,
            rootPath.relativize(subprojectDir.toPath()).invariantSeparatorsPathString,
            subprojectDir.hasDescendantDirWithFiles("src/test"),
            subprojectDir.hasDescendantDirWithFiles("src/integTest"),
            subprojectDir.hasDescendantDirWithFiles("src/crossVersionTest"),
            subprojectDir.hasFlakyCrossVersionTest()
        )
    }

    /**
     * Whether this subproject has a cross-version test that `-PflakyTests=ONLY` could select.
     *
     * That filter is a JUnit Platform tag include, evaluated during discovery inside the test JVM, so Gradle cannot
     * tell up front that a task will select nothing - it forks a JVM for every one regardless. Cross-version test
     * tasks are registered per tested Gradle version per subproject, so scheduling the subprojects without any
     * `@Flaky` cross-version test costs ~1000 JVM forks that discover nothing and exit, which does not fit in the
     * flaky test quarantine build's timeout unless the build cache happens to serve nearly all of it.
     *
     * The match is deliberately loose: a false positive only restores the old behaviour for one subproject, while a
     * false negative would silently drop a test from the quarantine build.
     */
    private
    fun File.hasFlakyCrossVersionTest(): Boolean {
        val dir = resolve("src/crossVersionTest")
        if (!dir.isDirectory) {
            return false
        }
        return dir.walk()
            .filter { it.isFile && it.extension in setOf("groovy", "java", "kt") }
            .any { file -> file.useLines { lines -> lines.any { "@Flaky" in it || FLAKY_ANNOTATION in it } } }
    }

    private
    fun File.hasDescendantDirWithFiles(descendant: String): Boolean {
        val dir = resolve(descendant)
        return dir.isDirectory && dir.walk().any { it.isFile }
    }
}
