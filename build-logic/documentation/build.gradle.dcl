groovyKotlinDslPlugin {
    description = "Provides a plugin to generate Gradle's DSL reference, User Manual and Javadocs"

    dependencies {
        implementation("gradlebuild:basics")
        implementation("gradlebuild:module-identity")

        implementation(project(":build-update-utils"))

        implementation(catalog("buildLibs.javaParserCore"))
        implementation(catalog("buildLibs.guava"))
        // FIXME: cannot use catalog() + action-taking dependency notation
        implementation("com.uwyn:jhighlight:1.0") {
            exclude(mapOf("module" to "servlet-api"))
        }
        implementation(catalog("buildLibs.flexmark"))
        implementation(catalog("buildLibs.gson"))
        implementation(catalog("buildLibs.commonsLang3"))
        implementation(catalog("buildLibs.asciidoctor"))
        implementation(catalog("buildLibs.asciidoctorJvm"))
        implementation(catalog("buildLibs.dokkaPlugin"))
        implementation(catalog("buildLibs.jspecify"))

        testImplementation(gradleTestKit())
    }

    gradlePlugins {
        gradlePlugin("gradleDocumentation") {
            id = "gradlebuild.documentation"
            implementationClass = "gradlebuild.docs.GradleBuildDocumentationPlugin"
        }
    }
}
