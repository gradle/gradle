plugins {
    `kotlin-dsl`
}

description = "Provides custom rules to be used in detekt checks"

group = "gradlebuild"

dependencies {
    compileOnly("io.gitlab.arturbosch.detekt:detekt-api:1.23.6")

    testImplementation("org.jetbrains:annotations:24.0.1")
    testImplementation("io.gitlab.arturbosch.detekt:detekt-test:1.23.6")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
