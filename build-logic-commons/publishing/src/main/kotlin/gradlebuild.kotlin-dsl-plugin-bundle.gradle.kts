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

import gradlebuild.basics.capitalize
import gradlebuild.pluginpublish.extension.PluginPublishExtension
import org.gradle.plugin.compatibility.compatibility
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
    signing
    `java-gradle-plugin`
    id("com.gradle.plugin-publish")
}

extensions.create<PluginPublishExtension>("pluginPublish", gradlePlugin)

tasks.validatePlugins {
    enableStricterValidation = true
}

// Remove gradleApi() and gradleTestKit() as we want to compile/run against Gradle modules
// TODO consider splitting `java-gradle-plugin` to provide only what's necessary here
configurations.all {
    withDependencies {
        remove(project.dependencies.gradleApi())
        remove(project.dependencies.gradleTestKit())
    }
}

publishing.publications.withType<MavenPublication>().configureEach {
    if (name == "pluginMaven") {
        groupId = project.group.toString()
        artifactId = gradleModule.identity.baseName.get()
    }
    pom {
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
    }
}

// publish plugin to local repository for integration testing -----------------
// See AbstractPluginTest
// Test builds apply the plugins at the version that Gradle expects, which is the published version.
// Thus the test repository holds the plugins of this build at that version, not at the version of this build.
// The version is compiled into the plugins jar, so the jar in the test repository gets a class with the published version.
val publishedKotlinDslPluginVersion by lazy { versionCatalogs.named("libs").findVersion("kotlinDslPlugins").get().requiredVersion }
val stagingRepository = layout.buildDirectory.dir("repository-staging")
val localRepository = layout.buildDirectory.dir("repository")
val checkoutVersion = gradleModule.identity.reproducibleVersion
val versionClassPath = "org/gradle/kotlin/dsl/plugins/VersionKt.class"

val publishPluginsToStagingRepository = tasks.register("publishPluginsToStagingRepository") {
    dependsOn("publishPluginMavenPublicationToStagingRepository")
    val repoDir = stagingRepository // Prevent capturing the Gradle script instance for configuration cache compatibility
    // This should be unified with publish-public-libraries if possible
    doLast {
        repoDir.get().asFileTree.matching { include("**/maven-metadata.xml") }.forEach {
            it.writeText(it.readText().replace("\\Q<lastUpdated>\\E\\d+\\Q</lastUpdated>\\E".toRegex(), "<lastUpdated>${Year.now().value}0101000000</lastUpdated>"))
        }
        repoDir.get().asFileTree.matching { include("**/*.module") }.forEach {
            val content = it.readText()
                .replace("\"buildId\":\\s+\"\\w+\"".toRegex(), "\"buildId\": \"\"")
                .replace("\"size\":\\s+\\d+".toRegex(), "\"size\": 0")
                .replace("\"sha512\":\\s+\"\\w+\"".toRegex(), "\"sha512\": \"\"")
                .replace("\"sha1\":\\s+\"\\w+\"".toRegex(), "\"sha1\": \"\"")
                .replace("\"sha256\":\\s+\"\\w+\"".toRegex(), "\"sha256\": \"\"")
                .replace("\"md5\":\\s+\"\\w+\"".toRegex(), "\"md5\": \"\"")
            it.writeText(content)
        }
    }
}

// GenerateKotlinDslPluginsExtensions generates the class. It holds the version as a string constant.
val writeTestRepositoryVersionClass = tasks.register("writeTestRepositoryVersionClass") {
    val pluginsJar = tasks.jar.flatMap { it.archiveFile }
    val classesDir = layout.buildDirectory.dir("test-repository-classes")
    val classPath = versionClassPath
    val checkout = checkoutVersion
    val published = publishedKotlinDslPluginVersion
    inputs.file(pluginsJar)
    inputs.property("checkoutVersion", checkout)
    inputs.property("publishedVersion", published)
    outputs.dir(classesDir)
    doLast {
        val original = ZipFile(pluginsJar.get().asFile).use { jar -> jar.getInputStream(jar.getEntry(classPath)).readBytes() }
        val reader = ClassReader(original)
        val writer = ClassWriter(reader, 0)
        reader.accept(object : ClassVisitor(Opcodes.ASM9, writer) {
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<String>?) =
                object : MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    override fun visitLdcInsn(value: Any) {
                        super.visitLdcInsn(if (value == checkout.get()) published else value)
                    }
                }
        }, 0)
        classesDir.get().file(classPath).asFile.apply { parentFile.mkdirs() }.writeBytes(writer.toByteArray())
    }
}

val testRepositoryPluginsJar = tasks.register<Jar>("testRepositoryPluginsJar") {
    destinationDirectory = layout.buildDirectory.dir("test-repository-jar")
    archiveFileName = "gradle-kotlin-dsl-plugins-$publishedKotlinDslPluginVersion.jar"
    from(zipTree(tasks.jar.flatMap { it.archiveFile })) {
        exclude(versionClassPath, "META-INF/MANIFEST.MF")
    }
    from(writeTestRepositoryVersionClass)
}

val publishPluginsToTestRepository = tasks.register<Sync>("publishPluginsToTestRepository") {
    dependsOn(publishPluginsToStagingRepository)
    val checkout = checkoutVersion
    val published = publishedKotlinDslPluginVersion
    val pluginsJarName = checkout.map { "gradle-kotlin-dsl-plugins-$it.jar" }
    from(stagingRepository) {
        // The checksums do not match the changed files
        exclude("**/*.md5", "**/*.sha1", "**/*.sha256", "**/*.sha512")
        eachFile {
            if (name == pluginsJarName.get()) {
                exclude()
            } else {
                path = path.replace(checkout.get(), published)
            }
        }
        filesMatching(listOf("**/*.pom", "**/*.module", "**/maven-metadata.xml")) {
            filter { line: String -> line.replace(checkout.get(), published) }
        }
    }
    from(testRepositoryPluginsJar) {
        into("org/gradle/kotlin/gradle-kotlin-dsl-plugins/$publishedKotlinDslPluginVersion")
    }
    into(localRepository)
    includeEmptyDirs = false
}

val futurePluginVersionsPropertiesFile = layout.buildDirectory.file("generated-resources/future-plugin-versions/future-plugin-versions.properties")
val writeFuturePluginVersions = tasks.register<WriteProperties>("writeFuturePluginVersions") {
    destinationFile = futurePluginVersionsPropertiesFile
}
val futurePluginVersionsDestDir = futurePluginVersionsPropertiesFile.map { it.asFile.parentFile }
sourceSets.main {
    output.dir(mapOf("builtBy" to writeFuturePluginVersions), futurePluginVersionsDestDir)
}
configurations.runtimeElements {
    outgoing {
        variants.named("resources") {
            artifact(futurePluginVersionsDestDir) {
                builtBy(writeFuturePluginVersions)
            }
        }
    }
}

publishing {
    repositories {
        maven {
            name = "staging"
            url = uri(stagingRepository)
        }
    }
}

gradlePlugin {
    website = "https://github.com/gradle/gradle/tree/HEAD/platforms/dsl/kotlin-dsl-plugins"
    vcsUrl = "https://github.com/gradle/gradle/tree/HEAD/platforms/dsl/kotlin-dsl-plugins"

    plugins.all {

        val plugin = this

        tags.addAll("Kotlin", "DSL")

        publishPluginsToStagingRepository.configure {
            dependsOn("publish${plugin.name.capitalize()}PluginMarkerMavenPublicationToStagingRepository")
        }

        writeFuturePluginVersions {
            property(plugin.id, version)
        }

        compatibility {
            features {
                configurationCache = true
                isolatedProjects = true
            }
        }
    }
}

// For local consumption by tests - this should be unified with publish-public-libraries if possible
configurations.create("localLibsRepositoryElements") {
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named("gradle-local-repository"))
    }
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(localRepository) {
        builtBy(publishPluginsToTestRepository)
    }
}

// Workaround for https://github.com/gradle/gradlecom/issues/627
configurations.archives.get().allArtifacts.removeIf {
    it.name != "plugins"
}

// The key ID is required because the signing key is a subkey.
val pgpSigningKeyId: Provider<String> = providers.environmentVariable("PGP_SIGNING_KEY_ID")
val pgpSigningKey: Provider<String> = providers.environmentVariable("PGP_SIGNING_KEY")
val pgpSigningPassPhrase: Provider<String> = providers.environmentVariable("PGP_SIGNING_KEY_PASSPHRASE")
val signArtifacts: Boolean = !pgpSigningKey.orNull.isNullOrEmpty()

tasks.withType<Sign>().configureEach { isEnabled = signArtifacts }

signing {
    useInMemoryPgpKeys(pgpSigningKeyId.orNull, pgpSigningKey.orNull, pgpSigningPassPhrase.orNull)
    publishing.publications.configureEach {
        if (signArtifacts) {
            signing.sign(this)
        }
    }
}
