plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
    id("gradlebuild.build-logic.groovy-dsl-gradle-plugin")
}

description = "Provides a plugin to generate Gradle's DSL reference, User Manual and Javadocs"

dependencies {
    implementation(project(":basics"))
    implementation(project(":module-identity"))
    implementation(project(":build-update-utils"))

    implementation("com.github.javaparser:javaparser-core")
    implementation("com.google.guava:guava")
    implementation("com.uwyn:jhighlight") {
        exclude(module = "servlet-api")
    }
    implementation("com.vladsch.flexmark:flexmark-all")
    implementation("commons-lang:commons-lang")
    implementation("org.asciidoctor:asciidoctor-gradle-jvm")
    implementation("org.asciidoctor:asciidoctorj")
    implementation("org.asciidoctor:asciidoctorj-pdf")
    implementation("dev.adamko.dokkatoo:dokkatoo-plugin")
    implementation("org.jetbrains.dokka:dokka-core")

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
