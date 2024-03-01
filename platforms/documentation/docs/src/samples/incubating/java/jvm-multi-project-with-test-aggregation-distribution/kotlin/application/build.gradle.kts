plugins {
    id("myproject.java-conventions")
    application
    id("test-report-aggregation") // <1>
}

dependencies {
    implementation(project(":list"))
    implementation(project(":utilities"))
}

application {
    mainClass = "org.gradle.sample.Main"
}

tasks.check {
    dependsOn(tasks.named<TestReport>("testAggregateTestReport")) // <2>
}
