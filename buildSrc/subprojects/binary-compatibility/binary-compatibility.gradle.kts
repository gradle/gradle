dependencies {
    compile(project(":buildPlatform"))
    compile(project(":kotlinDsl"))
    compile("com.google.code.gson:gson:2.8.2")
    compile("me.champeau.gradle:japicmp-gradle-plugin:0.2.7")
    compile("org.javassist:javassist:3.23.0-GA")
    compile("com.github.javaparser:javaparser-core")
    compile("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.0.5")

    testImplementation("org.jsoup:jsoup:1.11.3")
}

tasks {
    compileGroovy {
        classpath += files(compileKotlin)
    }
}
