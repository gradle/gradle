import build.*

plugins {
    id("public-kotlin-dsl-module")
}

base {
    archivesBaseName = "gradle-kotlin-dsl-provider-spi"
}

dependencies {
    compileOnly(gradleApi())
}
