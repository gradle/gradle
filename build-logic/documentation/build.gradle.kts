plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
    id("gradlebuild.build-logic.groovy-dsl-gradle-plugin")
}

description = "Plugins for assembling Gradle's reference documentation (Javadoc, DSL reference, Kotlin DSL reference) and the docs-site that publishes it."

dependencies {
    implementation("gradlebuild:basics")
    implementation("gradlebuild:module-identity")

    implementation(projects.buildUpdateUtils)

    implementation(buildLibs.gradleGuidesPlugin)
    implementation(buildLibs.javaParserCore)
    implementation(buildLibs.guava)
    implementation(buildLibs.jhighlight) {
        exclude(module = "servlet-api")
    }
    implementation(buildLibs.flexmark)
    implementation(buildLibs.gson)
    implementation(buildLibs.commonsLang3)
    implementation(buildLibs.asciidoctor)
    implementation(buildLibs.asciidoctorJvm)
    implementation(buildLibs.dokkaPlugin)
    implementation(buildLibs.jspecify)

    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        register("gradleDocumentation") {
            id = "gradlebuild.documentation"
            implementationClass = "gradlebuild.docs.GradleBuildDocumentationPlugin"
        }
    }
}
