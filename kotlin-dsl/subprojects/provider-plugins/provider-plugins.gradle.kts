import build.*

plugins {
    `public-kotlin-dsl-module`
}

base {
    archivesBaseName = "gradle-kotlin-dsl-provider-plugins"
}

dependencies {
    compileOnly(gradleApiWithParameterNames())

    compile(project(":provider"))
}
