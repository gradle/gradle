import com.gradleup.gr8.EmbeddedJarTask
import com.gradleup.gr8.Gr8Task
import gradlebuild.identity.extension.ModuleIdentityExtension
import java.util.jar.Attributes

/*
 * Copyright 2024 the original author or authors.
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

/**
 * Produces a minified JAR for the project.
 */

plugins {
    java
    id("com.gradleup.gr8")
}

interface MinifiedApplicationJar {
    /**
     * The minified JAR output.
     */
    val minifiedJar: Provider<RegularFile>

    /**
     * Use the given function to calculate the base name of the minified JAR, given the target Gradle version.
     */
    fun outputJarName(name: (GradleVersion) -> String)

    /**
     * Used in the minified JAR manifest.
     */
    val implementationTitle: Property<String>

    /**
     * Main class name for the application.
     */
    val mainClassName: Property<String>

    /**
     * Excludes the given resources from the minified JAR.
     */
    fun exclude(pattern: String)

    /**
     * Excludes the given resources from dependencies merged into the minified JAR.
     */
    fun excludeFromDependencies(pattern: String)
}

abstract class DefaultMinifiedJar : MinifiedApplicationJar {
    val excludes: MutableSet<String> = mutableSetOf()
    val excludeFromDependencies: MutableSet<String> = mutableSetOf()
    private var jarName: ((GradleVersion) -> String)? = null

    abstract override val minifiedJar: RegularFileProperty

    override fun exclude(pattern: String) {
        excludes.add(pattern)
    }

    override fun excludeFromDependencies(pattern: String) {
        excludeFromDependencies.add(pattern)
    }

    override fun outputJarName(name: (GradleVersion) -> String) {
        jarName = name
    }

    fun jarName(project: Project, version: GradleVersion): String {
        return jarName?.invoke(version) ?: "gradle-${project.name}-min-${version.version}.jar"
    }
}

val model = extensions.create(MinifiedApplicationJar::class.java, "application", DefaultMinifiedJar::class.java) as DefaultMinifiedJar

afterEvaluate {
    val minifiedLibsDir = layout.buildDirectory.dir("minified")

    val preMinifiedJar by tasks.registering(Jar::class) {
        archiveFileName = "gradle-${project.name}-pre-minify.jar"
        destinationDirectory = minifiedLibsDir
        from(layout.projectDirectory.dir("src/executable/resources"))
        from(sourceSets.main.get().output)
        manifest {
            attributes[Attributes.Name.MAIN_CLASS.toString()] = model.mainClassName.get()
            attributes[Attributes.Name.IMPLEMENTATION_TITLE.toString()] = model.implementationTitle.get()
        }
        for (pattern in model.excludes) {
            exclude(pattern)
        }
    }

    gr8 {
        create("gr8") {
            // TODO This should work by passing `executableJar` directly to th Gr8 plugin
            programJar(preMinifiedJar.flatMap { it.archiveFile })
            archiveName("gradle-${project.name}-minified.jar")
            configuration("runtimeClasspath")
            proguardFile("src/main/proguard/minified-jar.pro")
            for (pattern in model.excludes) {
                // GR8 uses regexp patterns
                exclude(pattern.toRegexp())
            }
            for (pattern in model.excludeFromDependencies) {
                // GR8 uses regexp patterns
                exclude(pattern.toRegexp())
            }
        }
    }

    // TODO This dependency should be configured by the Gr8 plugin
    tasks.named<EmbeddedJarTask>("gr8EmbeddedJar").configure {
        dependsOn(preMinifiedJar)
    }

    // TODO It does not seem possible to lazily configure the Gr8 output file name, so make a copy
    val intermediateMinifiedJar = tasks.named<Gr8Task>("gr8R8Jar").flatMap { it.outputJar() }
    val minifiedJarFileName = extensions.getByType<ModuleIdentityExtension>().version.map { model.jarName(project, it.baseVersion) }
    val minifiedJar by tasks.registering(Copy::class) {
        destinationDir = minifiedLibsDir.get().asFile
        from(intermediateMinifiedJar) {
            rename { minifiedJarFileName.get() }
        }
    }
    model.minifiedJar = minifiedJar.map { File(it.destinationDir, minifiedJarFileName.get()) }
}

private
fun String.toRegexp() = replace("*", ".*")
