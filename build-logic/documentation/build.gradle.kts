plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
    id("gradlebuild.build-logic.groovy-dsl-gradle-plugin")
}

description = "Provides a plugin to generate Gradle's DSL reference, User Manual and Javadocs"

dependencies {
    implementation("gradlebuild:basics")
    implementation("gradlebuild:module-identity")

    implementation(projects.buildUpdateUtils)

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
