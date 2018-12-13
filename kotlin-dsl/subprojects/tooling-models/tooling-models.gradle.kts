import build.futureKotlin

plugins {
    `public-kotlin-dsl-module`
}

base {
    archivesBaseName = "gradle-kotlin-dsl-tooling-models"
}

dependencies {
    implementation(futureKotlin("stdlib-jdk8"))
}
