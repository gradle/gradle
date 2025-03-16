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

package gradlebuild.shade.transforms

import com.google.gson.Gson
import gradlebuild.basics.classanalysis.JarAnalyzer
import gradlebuild.identity.tasks.BuildReceipt
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault


private
const val CLASS_TREE_FILE_NAME = "classTree.json"


private
const val ENTRY_POINTS_FILE_NAME = "entryPoints.json"


private
const val RELOCATED_CLASSES_DIR_NAME = "classes"


private
const val MANIFEST_FILE_NAME = "MANIFEST.MF"


@CacheableTransform
abstract class ShadeClasses : TransformAction<ShadeClasses.Parameters> {

    interface Parameters : TransformParameters {
        @get:Input
        val shadowPackage: Property<String>
        @get:Input
        val keepPackages: SetProperty<String>
        @get:Input
        val unshadedPackages: SetProperty<String>
        @get:Input
        val ignoredPackages: SetProperty<String>
    }

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val outputDirectory = outputs.dir("shadedClasses")
        val classesDir = outputDirectory.resolve(RELOCATED_CLASSES_DIR_NAME)
        classesDir.mkdir()
        val manifestFile = outputDirectory.resolve(MANIFEST_FILE_NAME)
        val buildReceiptFile = outputDirectory.resolve(BuildReceipt.buildReceiptFileName)

        val classGraph = JarAnalyzer(parameters.shadowPackage.get(), parameters.keepPackages.get(), parameters.unshadedPackages.get(), parameters.ignoredPackages.get()).analyze(input.get().asFile, classesDir, manifestFile, buildReceiptFile)

        outputDirectory.resolve(CLASS_TREE_FILE_NAME).bufferedWriter().use {
            Gson().toJson(classGraph.getDependencies(), it)
        }
        outputDirectory.resolve(ENTRY_POINTS_FILE_NAME).bufferedWriter().use {
            Gson().toJson(classGraph.entryPoints.map { it.outputClassFilename }, it)
        }
    }
}


@DisableCachingByDefault(because = "Only filters the input artifact")
abstract class FindClassTrees : TransformAction<TransformParameters.None> {
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        outputs.file(input.get().asFile.resolve(CLASS_TREE_FILE_NAME))
    }
}


@DisableCachingByDefault(because = "Only filters the input artifact")
abstract class FindEntryPoints : TransformAction<TransformParameters.None> {
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        outputs.file(input.get().asFile.resolve(ENTRY_POINTS_FILE_NAME))
    }
}


@DisableCachingByDefault(because = "Only filters the input artifact")
abstract class FindRelocatedClasses : TransformAction<TransformParameters.None> {
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        outputs.dir(input.get().asFile.resolve(RELOCATED_CLASSES_DIR_NAME))
    }
}


@DisableCachingByDefault(because = "Only filters the input artifact")
abstract class FindManifests : TransformAction<TransformParameters.None> {
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val manifest = input.get().asFile.resolve(MANIFEST_FILE_NAME)
        if (manifest.exists()) {
            outputs.file(manifest)
        }
    }
}


@DisableCachingByDefault(because = "Only filters the input artifact")
abstract class FindBuildReceipt : TransformAction<TransformParameters.None> {
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val manifest = input.get().asFile.resolve(BuildReceipt.buildReceiptFileName)
        if (manifest.exists()) {
            outputs.file(manifest)
        }
    }
}
