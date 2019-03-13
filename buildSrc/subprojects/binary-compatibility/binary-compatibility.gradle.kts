dependencies {
    compile(project(":buildPlatform"))
    compile(project(":kotlinDsl"))
    compile("com.google.code.gson:gson:2.8.2")
    compile("me.champeau.gradle:japicmp-gradle-plugin:0.2.4")
    compile("org.javassist:javassist:3.23.0-GA")
    compile("com.github.javaparser:javaparser-core")
}
