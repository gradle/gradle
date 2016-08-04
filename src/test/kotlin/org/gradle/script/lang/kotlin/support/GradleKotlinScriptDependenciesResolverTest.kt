package org.gradle.script.lang.kotlin.support

import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies
import org.jetbrains.kotlin.script.ScriptContents

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.MatcherAssert.assertThat

import java.io.File

class GradleKotlinScriptDependenciesResolverTest {

    @JvmField
    @Rule val tempFolder = TemporaryFolder()

    @Test
    fun `given buildSrc folder, it will include buildSrc source roots`() {
        withFolders {
            "project" {
                "buildSrc/src/main" {
                    +"foo"
                    +"bar"
                }
            }
            "gradle" {
                "src" {
                    +"gradle-foo"
                    +"gradle-bar"
                }
            }
        }

        val environment = mapOf(
            "projectRoot" to folder("project"),
            "gradleHome" to folder("gradle"))

        val dependencies = resolve(environment)

        assertThat(
            dependencies.sources,
            hasItems(
                folder("project/buildSrc/src/main/foo"),
                folder("project/buildSrc/src/main/bar"),
                folder("gradle/src/gradle-foo"),
                folder("gradle/src/gradle-bar")))
    }

    private fun resolve(environment: Environment): KotlinScriptExternalDependencies =
        GradleKotlinScriptDependenciesResolver().run {
            modelProvider = EmptyKotlinBuildScriptModelProvider
            resolve(EmptyScriptContents, environment, { s, m, p -> }, null)
        }.get()!!

   object EmptyScriptContents : ScriptContents {
        override val file: File? = null
        override val text: CharSequence? = ""
        override val annotations: Iterable<Annotation> = emptyList()
    }

    object EmptyKotlinBuildScriptModelProvider : KotlinBuildScriptModelProvider {
        override fun modelFor(environment: Map<String, Any?>) =
            StandardKotlinBuildScriptModel(emptyList())
    }

    fun withFolders(folders: FoldersDslExpression) =
        tempFolder.root.withFolders(folders)

    fun folder(path: String): File =
        File(tempFolder.root, path).canonicalFile
}

fun File.withFolders(folders: FoldersDslExpression) =
    apply { FoldersDsl(this).folders() }

typealias FoldersDslExpression = FoldersDsl.() -> Unit

class FoldersDsl(val root: File) {

    operator fun String.invoke(subFolders: FoldersDslExpression): File =
        (+this).withFolders(subFolders)

    operator fun String.unaryPlus(): File =
        File(root, this).apply { mkdirs() }.canonicalFile
}
