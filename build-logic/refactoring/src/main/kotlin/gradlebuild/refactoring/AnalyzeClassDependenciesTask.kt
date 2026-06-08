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

package gradlebuild.refactoring

import com.google.gson.GsonBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.net.URI

/**
 * A task that computes the transitive project-class dependency closure for a given set of root classes
 * (by FQCN and/or by package) and writes a JSON file optimized for agent consumption.
 *
 * The task reuses [DependencyAnalyzer] to perform the bytecode-driven traversal, then emits an entry
 * for each reachable project class with its direct one-hop dependencies, tagged as either belonging
 * to this project ([DependencyKind.PROJECT]) or to another Gradle subproject ([DependencyKind.SUBPROJECT]).
 * Third-party / external dependencies are intentionally omitted from the output.
 */
@DisableCachingByDefault(because = "Unable to snapshot ComponentIdentifier") // Same root cause as SplitProjectTask: gradle/gradle#36174
abstract class AnalyzeClassDependenciesTask : DefaultTask() {

    /**
     * A comma-separated list of Fully Qualified Class Names (FQCNs) to start the analysis from.
     */
    @get:Input
    @get:Optional
    @get:Option(option = "class", description = "Comma-separated list of FQCNs to analyze")
    abstract val rootClasses: Property<String>

    /**
     * A comma-separated list of package names to start the analysis from. Matches all classes whose
     * FQCN starts with `<package>.` (recursive into subpackages).
     */
    @get:Input
    @get:Optional
    @get:Option(option = "package", description = "Comma-separated list of packages to analyze (recursive)")
    abstract val rootPackages: Property<String>

    /**
     * Path to the JSON output file. Required.
     */
    @get:OutputFile
    @get:Option(option = "output", description = "Path to write the JSON output file")
    abstract val outputFile: RegularFileProperty

    /**
     * The directories containing the compiled class files of the current project.
     */
    @get:InputFiles
    @get:Classpath
    abstract val projectClassesDirs: ConfigurableFileCollection

    /**
     * The list of artifact files (JARs or directories) in the compile classpath. Must correspond
     * 1-to-1 with [compileClasspathComponentIds].
     */
    @get:InputFiles
    @get:Classpath
    abstract val compileClasspathFiles: ListProperty<File>

    /**
     * A list of component identifiers for the artifacts in the compile classpath. Must correspond
     * 1-to-1 with [compileClasspathFiles].
     */
    @get:Internal // Can be declared input after https://github.com/gradle/gradle/pull/36174
    abstract val compileClasspathComponentIds: ListProperty<ComponentIdentifier>

    /**
     * The source directories of the current project containing Java source files. Used to resolve
     * project-relative source paths for output entries.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val javaSourceDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val groovySourceDirectories: ConfigurableFileCollection

    @TaskAction
    fun analyze() {
        // 1. Read and validate options
        val rootClassNames = rootClasses.orNull.toCommaSet()
        val rootPackageNames = rootPackages.orNull.toCommaSet()
        validateInputs(rootClassNames, rootPackageNames)
        val output = outputFile.get().asFile

        // 2. Index project class FQCNs by walking projectClassesDirs
        val projectClassFqcns = indexProjectClassFqcns()

        // 3. Resolve root classes
        validateRootsResolve(rootClassNames, rootPackageNames, projectClassFqcns)
        val expandedRoots = buildSet {
            addAll(rootClassNames)
            rootPackageNames.forEach { pkg ->
                projectClassFqcns.filterTo(this) { it.startsWith("$pkg.") }
            }
        }

        // 4. Run the analyzer
        val result = DependencyAnalyzer().analyze(
            projectClassesDirs.files,
            compileClasspathFiles.get().toSet(),
            expandedRoots,
            followSubtypes = false
        )

        // 5. Resolve source files for each project class
        val sourceFiles = resolveSourceFiles(result.projectClasses)

        // 6. Build dependency-graph entries
        val artifactFilesToComponentIds: Map<File, ComponentIdentifier> =
            compileClasspathFiles.get().zip(compileClasspathComponentIds.get()).toMap()
        val subprojectPaths = sortedSetOf<String>()

        val projectClassEntries = result.projectClasses.sorted().map { fqcn ->
            val deps = result.dependencyGraph[fqcn] ?: emptySet()
            val depEntries = deps.mapNotNull { dep ->
                val depLocation = result.classLocationMap[dep] ?: return@mapNotNull null
                if (depLocation in result.projectClassesDirSet) {
                    DependencyJson(fqcn = dep, kind = "project", subproject = null)
                } else {
                    val componentId = artifactFilesToComponentIds[depLocation]
                    if (componentId is ProjectComponentIdentifier) {
                        subprojectPaths.add(componentId.projectPath)
                        DependencyJson(fqcn = dep, kind = "subproject", subproject = componentId.projectPath)
                    } else {
                        null
                    }
                }
            }.sortedWith(compareBy({ it.fqcn }, { it.kind }))

            ProjectClassJson(
                fqcn = fqcn,
                sourceFile = sourceFiles.getValue(fqcn),
                dependencies = depEntries
            )
        }

        // 7. Build top-level output
        val json = AnalysisJson(
            projectPath = project.path,
            roots = expandedRoots.sorted(),
            subprojectDependencies = subprojectPaths.toList(),
            projectClasses = projectClassEntries
        )

        // 8. Serialize with Gson (default: drop null fields so project-kind deps omit `subproject`)
        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        output.parentFile?.mkdirs()
        output.writeText(gson.toJson(json))

        // 9. Log the output location
        println("Wrote dependency analysis to ${output.asClickableFileUrl()}")
    }

    private fun validateInputs(rootClassNames: Set<String>, rootPackageNames: Set<String>) {
        if (rootClassNames.isEmpty() && rootPackageNames.isEmpty()) {
            throw GradleException("At least one of --class or --package must be specified.")
        }
        if (!outputFile.isPresent) {
            throw GradleException("--output is required.")
        }
    }

    private fun validateRootsResolve(
        rootClassNames: Set<String>,
        rootPackageNames: Set<String>,
        projectClassFqcns: Set<String>
    ) {
        val unknownClasses = rootClassNames.filter { it !in projectClassFqcns }
        val emptyPackages = rootPackageNames.filter { pkg ->
            projectClassFqcns.none { it.startsWith("$pkg.") }
        }
        if (unknownClasses.isEmpty() && emptyPackages.isEmpty()) return

        val sb = StringBuilder("Could not resolve all roots:")
        if (unknownClasses.isNotEmpty()) {
            sb.append("\n  Unknown classes:")
            unknownClasses.forEach { sb.append("\n    - ").append(it) }
        }
        if (emptyPackages.isNotEmpty()) {
            sb.append("\n  Empty packages (no project classes matched):")
            emptyPackages.forEach { sb.append("\n    - ").append(it) }
        }
        throw GradleException(sb.toString())
    }

    private fun indexProjectClassFqcns(): Set<String> {
        val result = mutableSetOf<String>()
        for (dir in projectClassesDirs.files) {
            if (!dir.isDirectory) continue
            val base = dir.toPath()
            dir.walk()
                .filter { it.isFile && it.extension == "class" }
                .forEach { file ->
                    val relative = base.relativize(file.toPath()).toString()
                    val fqcn = relative
                        .removeSuffix(".class")
                        .replace(File.separatorChar, '.')
                    result.add(fqcn)
                }
        }
        return result
    }

    /**
     * Resolves project-relative source-file paths for each FQCN.
     * Fails with a single aggregated error if any class has no source file.
     */
    private fun resolveSourceFiles(projectClasses: Set<String>): Map<String, String> {
        val javaDirs = javaSourceDirectories.files
        val groovyDirs = groovySourceDirectories.files
        val resolved = mutableMapOf<String, String>()
        val missing = mutableListOf<String>()

        projectClasses.forEach { fqcn ->
            val relativePath = toRelativeSourceFilePath(fqcn)
            var found: String? = null
            for (dir in javaDirs) {
                val sourceFile = File(dir, "$relativePath.java")
                if (sourceFile.exists()) {
                    found = "src/main/java/$relativePath.java"
                    break
                }
            }
            if (found == null) {
                outer@ for (dir in groovyDirs) {
                    for (ext in listOf("groovy", "java")) {
                        val sourceFile = File(dir, "$relativePath.$ext")
                        if (sourceFile.exists()) {
                            found = "src/main/groovy/$relativePath.$ext"
                            break@outer
                        }
                    }
                }
            }
            if (found != null) {
                resolved[fqcn] = found
            } else {
                missing.add(fqcn)
            }
        }

        if (missing.isNotEmpty()) {
            throw GradleException("Could not find source files for the following classes:\n" + missing.joinToString("\n"))
        }
        return resolved
    }

    private fun toRelativeSourceFilePath(fqcn: String): String {
        val topLevel = if (fqcn.contains('$')) fqcn.substringBefore('$') else fqcn
        return topLevel.replace('.', '/')
    }

    private fun String?.toCommaSet(): Set<String> =
        this.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun File.asClickableFileUrl(): String =
        URI("file", "", toURI().path, null, null).toASCIIString()

    private data class AnalysisJson(
        val projectPath: String,
        val roots: List<String>,
        val subprojectDependencies: List<String>,
        val projectClasses: List<ProjectClassJson>
    )

    private data class ProjectClassJson(
        val fqcn: String,
        val sourceFile: String,
        val dependencies: List<DependencyJson>
    )

    private data class DependencyJson(
        val fqcn: String,
        val kind: String,
        val subproject: String?
    )
}
