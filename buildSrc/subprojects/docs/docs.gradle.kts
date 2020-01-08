dependencies {
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
    implementation("com.vladsch.flexmark:flexmark-all:0.34.56")
    implementation("com.uwyn:jhighlight:1.0") {
        exclude(module = "servlet-api")
    }

    api("com.google.guava:guava")
    api("org.asciidoctor:asciidoctor-gradle-plugin:1.5.10")

    implementation(project(":buildPlatform"))
    implementation("org.asciidoctor:asciidoctorj:1.5.8.1")
    implementation("commons-lang:commons-lang:2.6")
    implementation("org.asciidoctor:asciidoctorj-pdf:1.5.0-alpha.16")
    implementation("com.github.javaparser:javaparser-core")
}

gradlePlugin {
    plugins {
        register("gradleDocumentation") {
            id = "gradlebuild.documentation"
            implementationClass = "org.gradle.gradlebuild.docs.GradleBuildDocumentationPlugin"
        }
    }
}
