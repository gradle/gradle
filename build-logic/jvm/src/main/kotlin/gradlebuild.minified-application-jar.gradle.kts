import gradlebuild.MinifiedApplicationJar
import gradlebuild.application.MinifiedJar
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
 * Produces a minified application JAR for the project. The JAR is not wired as an input to anything.
 */

plugins {
    java
    id("com.gradleup.gr8")
}

abstract class DefaultMinifiedJar : MinifiedApplicationJar {
    abstract val excludeResources: SetProperty<String>
    abstract val excludeResourcesFromDependencies: SetProperty<String>

    private
    var jarName: ((GradleVersion) -> String)? = null

    abstract override val minifiedJar: RegularFileProperty

    override fun excludeResources(pattern: String) {
        excludeResources.add(pattern)
    }

    override fun excludeResourcesFromDependencies(pattern: String) {
        excludeResourcesFromDependencies.add(pattern)
    }

    override fun outputJarName(name: (GradleVersion) -> String) {
        jarName = name
    }

    fun jarName(projectName: String, version: GradleVersion): String {
        return jarName?.invoke(version) ?: "gradle-${projectName}-${version.version}.jar"
    }
}

val model = extensions.create(MinifiedApplicationJar::class.java, "application", DefaultMinifiedJar::class.java) as DefaultMinifiedJar

val minifiedLibsDir = layout.buildDirectory.dir("minified")

/**
 * Assemble a JAR containing the input files.
 */
val preMinifiedJar by tasks.registering(Jar::class) {
    archiveFileName = "gradle-${project.name}-pre-minify.jar"
    destinationDirectory = minifiedLibsDir
    from(layout.projectDirectory.dir("src/executable/resources"))
    from(sourceSets.main.get().output)
    manifest {
        attributes[Attributes.Name.MAIN_CLASS.toString()] = model.mainClassName.get()
        attributes[Attributes.Name.IMPLEMENTATION_TITLE.toString()] = model.implementationTitle.get()
    }
}

/**
 * Create the minified JAR.
 */
val minifiedJar by tasks.registering(MinifiedJar::class) {
    val moduleIdentity = project.extensions.getByType<ModuleIdentityExtension>()
    val projectName = project.name
    val minifiedJarFileName = moduleIdentity.version.map { model.jarName(projectName, it.baseVersion) }

    inputJar = preMinifiedJar.flatMap { it.archiveFile }
    outputJar = providers.zip(minifiedLibsDir, minifiedJarFileName) { dir, jar -> dir.file(jar) }
    classpath.from(configurations.runtimeClasspath)
    excludeResources = model.excludeResources
    excludeResourcesFromDependencies = model.excludeResourcesFromDependencies
    keepClasses.add(model.mainClassName)
    // Need to keep the package-info for the main class, as the minified JAR is currently used by architecture tests and these need the annotations on package-info
    keepClasses.add(model.mainClassName.map { it.substringBeforeLast(".") + ".package-info" })
}

model.minifiedJar = minifiedJar.flatMap { it.outputJar }
