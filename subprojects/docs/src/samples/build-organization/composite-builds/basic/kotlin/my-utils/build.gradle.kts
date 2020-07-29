plugins {
    idea
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "idea")

    group = "org.sample"
    version = "1.0"

    repositories {
        jcenter()
    }
}

project(":string-utils") {
    dependencies {
        "implementation"("org.apache.commons:commons-lang3:3.4")
    }
}
