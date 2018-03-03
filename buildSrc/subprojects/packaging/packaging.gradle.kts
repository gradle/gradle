plugins {
    `java-gradle-plugin`
}

apply { plugin("org.gradle.kotlin.kotlin-dsl") }

dependencies {
    compile(project(":configuration"))
    compile(project(":kotlinDsl"))
    compile("com.google.guava:guava-jdk5:14.0.1")
    compile("org.ow2.asm:asm:6.0")
    compile("org.ow2.asm:asm-commons:6.0")
}

gradlePlugin {
    (plugins) {
        "minify" {
            id = "minify"
            implementationClass = "org.gradle.gradlebuild.packaging.MinifyPlugin"
        }
    }
}


