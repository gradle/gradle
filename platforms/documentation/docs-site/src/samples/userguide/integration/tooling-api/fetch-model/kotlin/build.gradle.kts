plugins {
    `java-library`
}

dependencies {
    implementation(gradleApi())
    implementation(fileTree("${gradle.gradleHomeDir}/lib") { include("gradle-tooling-api-*.jar") })
}
