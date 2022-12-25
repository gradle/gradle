plugins {
    id("myproject.java-conventions")
    application
    id("jacoco-report-aggregation") // <1>
}

dependencies {
    implementation(project(":list"))
    implementation(project(":utilities"))
}

application {
    mainClass.set("org.gradle.sample.Main")
}

tasks.check {
    dependsOn(tasks.named<JacocoReport>("testCodeCoverageReport")) // <2>
}
