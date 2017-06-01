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
    compile(gradleScriptKotlinApi())
    compile(kotlinModule("stdlib"))
}

repositories {
    gradleScriptKotlin()
}
