buildscript {

    repositories {
        gradleScriptKotlin()
    }

    dependencies {
        classpath(kotlinModule("gradle-plugin", version = "1.0.3"))
    }
}

apply {
    plugin("kotlin")
    plugin<ApplicationPlugin>()
}

configure<ApplicationPluginConvention> {
    mainClassName = "samples.HelloWorldKt"
}

repositories {
    gradleScriptKotlin()
}

dependencies {
    compile(kotlinModule("stdlib", version = "1.0.3"))
}
