buildscript {
    repositories {
        gradleScriptKotlin()
    }
    dependencies {
        classpath(kotlinModule("gradle-plugin"))
    }
}

apply {
    plugin("kotlin")
}

dependencies {
    compile(kotlinModule("stdlib"))
}
