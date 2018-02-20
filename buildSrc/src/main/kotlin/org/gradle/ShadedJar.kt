package org.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.io.File
import java.util.LinkedHashSet


@CacheableTask
open class ShadedJar : DefaultTask() {
    /**
     * The source files to generate the jar from.
     */
    @Classpath
    var sourceFiles: FileCollection? = null

    /**
     * The directory to write temporary class files to.
     */
    @OutputDirectory
    var classesDir: File? = null

    /**
     * The output Jar file.
     */
    @OutputFile
    var jarFile: File? = null

    /**
     * File to write the text analysis report to.
     */
    @OutputFile
    var analysisFile: File? = null

    /**
     * The package name to prefix all shaded class names with.
     */
    @Input
    var shadowPackage: String? = null

    /**
     * Retain only those classes in the keep package hierarchies, plus any classes that are reachable from these classes.
     */
    @Input
    var keepPackages: Set<String> = LinkedHashSet()

    /**
     * Do not rename classes in the unshaded package hierarchies. Always includes 'java'.
     */
    @Input
    var unshadedPackages: Set<String> = LinkedHashSet()

    /**
     * Do not retain classes in the ingore packages hierarchies, unless reachable from some other retained class.
     */
    @Input
    var ignorePackages: Set<String> = LinkedHashSet()

    @TaskAction
    fun run() {
        ShadedJarCreator(sourceFiles!!, jarFile!!, analysisFile!!, classesDir!!, shadowPackage!!, keepPackages, unshadedPackages, ignorePackages).createJar()
    }
}
