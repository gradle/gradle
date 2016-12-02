buildscript {
    dependencies {
        classpath(kotlinModule("gradle-plugin"))
    }
}

plugins {
    application
}

apply {
    plugin("kotlin")
}

configure<ApplicationPluginConvention> {
    mainClassName = "cli.Main"
}

dependencies {
    compile(project(":core"))
    compile(kotlinModule("stdlib"))
}
