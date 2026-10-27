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

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.time.Year
import java.util.zip.ZipFile

plugins {
    id("gradlebuild.module-identity")
    `maven-publish`
    `java-gradle-plugin`
}

// Tests apply the Kotlin DSL plugins built from sources, from a local test repository.
// Gradle expects the currently published version of the plugins, so the test repository has them at that version.
// The publications are set up with the next version, which is what normally goes to the Plugin Portal.
// Thus the plugins are published to a staging repository first, then copied to the test repository at the published version.

val publishedVersion by lazy { versionCatalogs.named("libs").findVersion("kotlinDslPlugins").get().requiredVersion }
val nextVersion = gradleModule.identity.reproducibleVersion
val pluginsJar = tasks.jar.flatMap { it.archiveFile }
val pluginsArtifactName = gradleModule.identity.baseName
val pluginsArtifactDir = gradleModule.identity.group.zip(pluginsArtifactName) { group, name -> "${group.replace('.', '/')}/$name" }
val versionClassPath = "org/gradle/kotlin/dsl/plugins/VersionKt.class"
val stagingRepository = layout.buildDirectory.dir("repository-staging")
val testRepository = layout.buildDirectory.dir("repository")

publishing {
    repositories {
        maven {
            name = "staging"
            url = uri(stagingRepository)
        }
    }
}

val publishPluginsToStagingRepository = tasks.register("publishPluginsToStagingRepository") {
    dependsOn(tasks.withType<PublishToMavenRepository>().matching { it.repository.name == "staging" })
}

// The kotlin-dsl plugin warns when its version is not the version that Gradle expects. It reads its version from VersionKt.
// Thus the jar in the test repository needs a VersionKt with the published version, or every test build shows the warning.
val writeTestRepositoryVersionClass = tasks.register("writeTestRepositoryVersionClass") {
    val jar = pluginsJar.get().asFile
    val classesDir = layout.buildDirectory.dir("test-repository-classes").get()
    val classPath = versionClassPath
    val next = nextVersion.get()
    val published = publishedVersion
    inputs.file(pluginsJar)
    inputs.property("nextVersion", nextVersion)
    inputs.property("publishedVersion", publishedVersion)
    outputs.dir(classesDir)
    doLast {
        val original = ZipFile(jar).use { it.getInputStream(it.getEntry(classPath)).readBytes() }
        val reader = ClassReader(original)
        val writer = ClassWriter(reader, 0)
        reader.accept(object : ClassVisitor(Opcodes.ASM9, writer) {
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<String>?) =
                object : MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    override fun visitLdcInsn(value: Any) {
                        super.visitLdcInsn(if (value == next) published else value)
                    }
                }
        }, 0)
        classesDir.file(classPath).asFile.apply { parentFile.mkdirs() }.writeBytes(writer.toByteArray())
    }
}

val testRepositoryPluginsJar = tasks.register<Jar>("testRepositoryPluginsJar") {
    val published = publishedVersion
    destinationDirectory = layout.buildDirectory.dir("test-repository-jar")
    archiveFileName = pluginsArtifactName.map { "$it-$published.jar" }
    from(zipTree(pluginsJar)) {
        exclude(versionClassPath, "META-INF/MANIFEST.MF")
    }
    from(writeTestRepositoryVersionClass)
}

val publishPluginsToTestRepository = tasks.register<Sync>("publishPluginsToTestRepository") {
    val next = nextVersion.get()
    val published = publishedVersion
    val stagedPluginsJarName = "${pluginsArtifactName.get()}-$next.jar"
    from(files(stagingRepository).builtBy(publishPluginsToStagingRepository)) {
        // The checksums do not match the changed files
        exclude("**/*.md5", "**/*.sha1", "**/*.sha256", "**/*.sha512")
        eachFile {
            if (name == stagedPluginsJarName) {
                exclude()
            } else {
                path = path.replace(next, published)
            }
        }
        filesMatching(listOf("**/*.pom", "**/*.module", "**/maven-metadata.xml")) {
            filter { line: String -> line.replace(next, published) }
        }
        // This should be unified with publish-public-libraries if possible
        filesMatching("**/maven-metadata.xml") {
            filter { line: String -> line.replace("\\Q<lastUpdated>\\E\\d+\\Q</lastUpdated>\\E".toRegex(), "<lastUpdated>${Year.now().value}0101000000</lastUpdated>") }
        }
        filesMatching("**/*.module") {
            filter { line: String ->
                line
                    .replace("\"buildId\":\\s+\"\\w+\"".toRegex(), "\"buildId\": \"\"")
                    .replace("\"size\":\\s+\\d+".toRegex(), "\"size\": 0")
                    .replace("\"sha512\":\\s+\"\\w+\"".toRegex(), "\"sha512\": \"\"")
                    .replace("\"sha1\":\\s+\"\\w+\"".toRegex(), "\"sha1\": \"\"")
                    .replace("\"sha256\":\\s+\"\\w+\"".toRegex(), "\"sha256\": \"\"")
                    .replace("\"md5\":\\s+\"\\w+\"".toRegex(), "\"md5\": \"\"")
            }
        }
    }
    from(testRepositoryPluginsJar) {
        into(pluginsArtifactDir.map { "$it/$published" })
    }
    into(testRepository)
    includeEmptyDirs = false
}

// For local consumption by tests - this should be unified with publish-public-libraries if possible
configurations.create("localLibsRepositoryElements") {
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named("gradle-local-repository"))
    }
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(testRepository) {
        builtBy(publishPluginsToTestRepository)
    }
}
