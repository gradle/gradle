/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.gradlebuild.PublicApi
import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import org.gradle.gradlebuild.versioning.buildVersion

plugins {
    gradlebuild.internal.java
    gradlebuild.`binary-compatibility`
}

// Remove any pre-configured archives
configurations.all {
    artifacts.clear()
}

tasks.withType<AbstractArchiveTask>().configureEach {
    archiveBaseName.set("gradle")
    archiveVersion.set(rootProject.buildVersion.baseVersion)

    // The CI server looks for the distributions at this location
    destinationDirectory.set(rootProject.layout.buildDirectory.dir(rootProject.base.distsDirName))
}

tasks.named("clean").configure {
    delete(tasks.withType<AbstractArchiveTask>())
}

configurations {
    create("dists")

    create("buildReceipt") {
        isVisible = false
        isCanBeResolved = true
        isCanBeConsumed = false
        extendsFrom(configurations["gradleRuntimeSource"])
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attributes.attribute(Attribute.of("org.gradle.api", String::class.java), "build-receipt")
    }

    create("gradleFullDocs") {
        isVisible = false
        isCanBeResolved = true
        isCanBeConsumed = false
        extendsFrom(configurations["gradleDocumentation"])
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named("docs"))
        attributes.attribute(Attribute.of("type", String::class.java), "full-docs")
    }

    create("gradleGettingStarted") {
        isVisible = false
        isCanBeResolved = true
        isCanBeConsumed = false
        extendsFrom(configurations["gradleDocumentation"])
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named("docs"))
        attributes.attribute(Attribute.of("type", String::class.java), "getting-started")
    }

    create("minimalRuntime") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            attribute(Attribute.of("org.gradle.runtime", String::class.java), "minimal")
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        }
    }
}

dependencies {
    "minimalRuntime"(project(":core"))
    "minimalRuntime"(project(":dependencyManagement"))
    "minimalRuntime"(project(":platformJvm"))

    testImplementation(testFixtures(project(":core")))

    integTestImplementation(project(":baseServices"))
    integTestImplementation(project(":logging"))
    integTestImplementation(project(":coreApi"))
    integTestImplementation(library("guava"))
    integTestImplementation(library("commons_io"))
    integTestImplementation(library("ant"))
    integTestRuntimeOnly(project(":runtimeApiInfo"))
}

ext["zipRootFolder"] = "gradle-$version"

ext["binDistImage"] = copySpec {
    from("$rootDir/LICENSE")
    from("src/toplevel")
    into("bin") {
        from(configurations.gradleScripts)
        fileMode = Integer.parseInt("0755", 8)
    }
    into("lib") {
        from(configurations.coreGradleRuntime)
        into("plugins") {
            from(configurations["builtInGradlePlugins"] - configurations["coreGradleRuntime"])
        }
    }
}

ext["binWithDistDocImage"] = copySpec {
    with(ext["binDistImage"] as CopySpec)
    from(configurations["gradleGettingStarted"])
}

ext["allDistImage"] = copySpec {
    with(ext["binWithDistDocImage"] as CopySpec)
    // TODO: Change this to a src publication too
    rootProject.subprojects {
        val subproject = this
        subproject.plugins.withId("gradlebuild.java-library") { // TODO do not include internal projects here?
            into("src/${subproject.projectDir.name}") {
                from(subproject.sourceSets.main.get().allSource)
            }
        }
    }
    into("docs") {
        from(configurations["gradleFullDocs"])
        exclude("samples/**")
    }
}

ext["docsDistImage"] = copySpec {
    from("$rootDir/LICENSE")
    from("src/toplevel")
    into("docs") {
        from(configurations["gradleFullDocs"])
    }
}

val allZip = tasks.register<Zip>("allZip") {
    archiveClassifier.set("all")
    into(ext["zipRootFolder"]) {
        with(ext["allDistImage"] as CopySpec)
    }
}

val binZip = tasks.register<Zip>("binZip") {
    archiveClassifier.set("bin")
    into(ext["zipRootFolder"]) {
        with(ext["binWithDistDocImage"] as CopySpec)
    }
}

val srcZip = tasks.register<Zip>("srcZip") {
    archiveClassifier.set("src")
    into(ext["zipRootFolder"]) {
        from(rootProject.file("gradlew")) {
            fileMode = Integer.parseInt("0755", 8)
        }
        from(rootProject.projectDir) {
            val spec = this
            // TODO: Maybe make this some kind of publication too.
            listOf("buildSrc", "buildSrc/subprojects/*", "subprojects/*").forEach {
                spec.include("$it/*.gradle")
                spec.include("$it/*.gradle.kts")
                spec.include("$it/src/")
            }
            include("gradle.properties")
            include("buildSrc/gradle.properties")
            include("config/")
            include("gradle/")
            include("src/")
            include("*.gradle")
            include("*.gradle.kts")
            include("wrapper/")
            include("gradlew.bat")
            include("version.txt")
            include("released-versions.json")
            exclude("**/.gradle/")
        }
    }
}

val docsZip = tasks.register<Zip>("docsZip") {
    archiveClassifier.set("docs")
    into(ext["zipRootFolder"]) {
        with(ext["docsDistImage"] as CopySpec)
    }
}

tasks.register("buildDists") {
    dependsOn(allZip, binZip, srcZip, docsZip)
}

tasks.register<Zip>("outputsZip") {
    archiveFileName.set("outputs.zip")
    from(configurations["buildReceipt"])
    from(allZip)
    from(binZip)
    from(srcZip)
}

artifacts {
    add("dists", allZip)
    add("dists", binZip)
    add("dists", srcZip)
}

val integTestTasks: DomainObjectCollection<IntegrationTest> by extra
integTestTasks.configureEach {
    binaryDistributions.distributionsRequired = true
    systemProperty("org.gradle.public.api.includes", PublicApi.includes.joinToString(separator = ":"))
    systemProperty("org.gradle.public.api.excludes", PublicApi.excludes.joinToString(separator = ":"))
}
