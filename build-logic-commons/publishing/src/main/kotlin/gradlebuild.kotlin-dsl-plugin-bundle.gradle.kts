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

import gradlebuild.pluginpublish.extension.PluginPublishExtension
import org.gradle.plugin.compatibility.compatibility

plugins {
    id("gradlebuild.module-identity")
    `maven-publish`
    signing
    `java-gradle-plugin`
    id("com.gradle.plugin-publish")
    id("gradlebuild.kotlin-dsl-plugin-bundle-local-testing")
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

gradlePlugin {
    website = "https://github.com/gradle/gradle/tree/HEAD/platforms/dsl/kotlin-dsl-plugins"
    vcsUrl = "https://github.com/gradle/gradle/tree/HEAD/platforms/dsl/kotlin-dsl-plugins"

    plugins.all {

        tags.addAll("Kotlin", "DSL")

        compatibility {
            features {
                configurationCache = true
                isolatedProjects = true
            }
        }
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
