buildscript {
    dependencies {
        classpath(kotlinModule("gradle-plugin"))
    }
}

apply {
    plugin("kotlin")
    plugin<ApplicationPlugin>()
}

configure<ApplicationPluginConvention> {
    mainClassName = "cli.Main"
}

dependencies {
    compile(project(":core"))
    compile(kotlinModule("stdlib"))
}
