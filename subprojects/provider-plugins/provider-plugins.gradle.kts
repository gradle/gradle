import build.*

plugins {
    id("public-kotlin-dsl-module")
}

base {
    archivesBaseName = "gradle-kotlin-dsl-provider-plugins"
}

dependencies {
    compileOnly(gradleApiWithParameterNames())

    compile(project(":provider"))
}
