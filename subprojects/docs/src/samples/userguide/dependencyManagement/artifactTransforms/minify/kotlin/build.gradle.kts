/*
 * Copyright 2019 the original author or authors.
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

// tag::artifact-transform-minify[]
@CacheableTransform
abstract class Minify : TransformAction<Minify.Parameters> {
    interface Parameters : TransformParameters {
        @get:Input
        var keepClassesByArtifact: Map<String, Set<String>>
    }

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override
    fun transform(outputs: TransformOutputs) {
        for (entry in parameters.keepClassesByArtifact) {
            val fileName = inputArtifact.get().asFile.name
            if (fileName.startsWith(entry.key)) {
                val nameWithoutExtension = fileName.substring(0, filename.length - 4)
                minify(inputArtifact.get().asFile, entry.value, outputs.file("$nameWithoutExtension-min.jar"))
                return
            }
        }
        outputs.file(artifact)
    }

    private
    fun minify(artifact: File, keepClasses: Set<String>, jarFile: File) {
        // Implementation
// end::artifact-transform-minify[]
        JarOutputStream(BufferedOutputStream(FileOutputStream(jarFile))).use { jarOutputStream ->
            ZipFile(artifact).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) {
                        jarOutputStream.putNextEntry(ZipEntry(entry.name))
                        jarOutputStream.closeEntry()
                    } else if (entry.name.endsWith(".class")) {
                        val className = entry.name.replace("/", ".").substring(0, entry.name.length - 6)
                        if (keepClasses.contains(className)) {
                            jarOutputStream.putNextEntry(ZipEntry(entry.name))
                            zip.getInputStream(entry).withCloseable { jarOutputStream.wri << it }
                            jarOutputStream.closeEntry()
                        }
                    }
                    if (manifestFile.exists()) {
                        jarOutputStream.addJarEntry(JarFile.MANIFEST_NAME, manifestFile)
                    }
                    val visited = linkedSetOf<ClassDetails>()
                    for (classDetails in classGraph.entryPoints) {
                        visitTree(classDetails, classesDir, jarOutputStream, visited)
                    }
                }
            }
        }
// tag::artifact-transform-minify[]
    }
}
// end::artifact-transform-minify[]

val usage = Attribute.of("usage", String::class.java)
// tag::artifact-transform-registration[]
val minified = Attribute.of("minified", Boolean::class.javaObjectType)
val keepPatterns = mapOf(
    "fastutil" to setOf(
        "it.unimi.dsi.fastutil.ints.IntOpenHashSet",
        "it.unimi.dsi.fastutil.ints.IntSets"
    )
)

dependencies {
    registerTransform(Minify::class) {
        from.attribute(minified, false)
        to.attribute(minified, true)

        parameters {
            keepClasses = keepPatterns
        }
    }
}
// end::artifact-transform-registration[]


allprojects {
    dependencies {
        attributesSchema {
            attribute(usage)
        }
    }
    configurations.create("compile") {
        attributes.attribute(usage, "api")
    }
}
