dependencies {
    api("me.champeau.gradle:japicmp-gradle-plugin:0.2.9")

    implementation(project(":kotlinDsl"))

    implementation("com.google.code.gson:gson:2.8.2")
    implementation("org.javassist:javassist:3.23.0-GA")
    implementation("com.github.javaparser:javaparser-core")
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.1.0")
    implementation("com.google.guava:guava")

    testImplementation("org.jsoup:jsoup:1.11.3")
}

tasks {
    compileGroovy {
        classpath += files(compileKotlin)
    }
}
