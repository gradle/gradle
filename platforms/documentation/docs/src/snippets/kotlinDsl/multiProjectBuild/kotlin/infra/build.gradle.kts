plugins {
    `java-library`
    id("com.gradleup.shadow")
}

tasks.shadowJar {
    minimize()
}
