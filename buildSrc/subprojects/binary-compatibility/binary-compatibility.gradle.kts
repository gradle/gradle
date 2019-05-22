dependencies {
    api("me.champeau.gradle:japicmp-gradle-plugin:0.2.7")

    implementation(project(":buildPlatform"))
    implementation(project(":kotlinDsl"))
    implementation("com.google.code.gson:gson:2.8.2")
    implementation("org.javassist:javassist:3.23.0-GA")
    implementation("com.github.javaparser:javaparser-core")
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.0.5")

    testImplementation("org.jsoup:jsoup:1.11.3")
}

tasks {
    compileGroovy {
        classpath += files(compileKotlin)
    }
}
