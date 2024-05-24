import com.gradleup.gr8.EmbeddedJarTask
import com.gradleup.gr8.Gr8Task
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

plugins {
    java
    id("com.gradleup.gr8")
}

interface MinifiedJar {
    /**
     * The minified JAR output.
     */
    val minifiedJar: Provider<RegularFile>

    /**
     * Used in the minified JAR manifest. Can be undefined, in which case a default is used.
     */
    val implementationTitle: Property<String>

    /**
     * Excludes the given resources from the minified JAR.
     */
    fun exclude(pattern: String)

    /**
     * Excludes the given resources from dependencies merged into the minified JAR.
     */
    fun excludeFromDependencies(pattern: String)
}

abstract class DefaultMinifiedJar : MinifiedJar {
    val excludes: MutableSet<String> = mutableSetOf()
    val excludeFromDependencies: MutableSet<String> = mutableSetOf()

    abstract override val minifiedJar: RegularFileProperty

    override fun exclude(pattern: String) {
        excludes.add(pattern)
    }

    override fun excludeFromDependencies(pattern: String) {
        excludeFromDependencies.add(pattern)
    }
}

val model = extensions.create(MinifiedJar::class.java, "minify", DefaultMinifiedJar::class.java) as DefaultMinifiedJar

afterEvaluate {
    val executableJar by tasks.registering(Jar::class) {
        archiveFileName = "gradle-${project.name}-pre-minify.jar"
        from(layout.projectDirectory.dir("src/executable/resources"))
        from(sourceSets.main.get().output)
        if (model.implementationTitle.isPresent) {
            manifest {
                attributes[Attributes.Name.IMPLEMENTATION_TITLE.toString()] = model.implementationTitle.get()
            }
        }
        for (pattern in model.excludes) {
            exclude(pattern)
        }
        eachFile {
            println("-> COPY $name")
        }
        doFirst {
            println("-> USING EXCLUDES: $excludes")
            println("-> USING ATTRIBUTES: ${manifest.attributes}")
        }
    }

    gr8 {
        create("gr8") {
            // TODO This should work by passing `executableJar` directly to th Gr8 plugin
            programJar(executableJar.flatMap { it.archiveFile })
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
        dependsOn(executableJar)
    }

    model.minifiedJar = tasks.named<Gr8Task>("gr8R8Jar").flatMap { it.outputJar() }
}

private
fun String.toRegexp() = replace("*", ".*")
