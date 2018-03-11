import build.futureKotlin

plugins {
    id("kotlin-dsl-plugin-bundle")
}

base {
    archivesBaseName = "gradle-kotlin-dsl-plugins-experiments"
}

dependencies {
    compileOnly(gradleKotlinDsl())

    implementation(futureKotlin("stdlib-jdk8"))

    testImplementation(project(":test-fixtures"))
}
