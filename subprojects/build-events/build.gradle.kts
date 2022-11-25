plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation of build event services and build event types (work item, tasks, tests, configuration, etc)"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":core-api"))
    implementation(project(":core"))
    implementation(project(":model-core"))
    implementation(project(":tooling-api"))

    implementation(libs.jsr305)
    implementation(libs.guava)

    testImplementation(project(":internal-testing"))
    testImplementation(project(":model-core"))

    integTestImplementation(project(":logging")) {
        because("This isn't declared as part of integtesting's API, but should be as logging's classes are in fact visible on the API")
    }
    integTestImplementation(project(":build-option"))
    integTestImplementation(project(":enterprise-operations"))

    integTestDistributionRuntimeOnly(project(":distributions-basics"))  {
        because("Requires ':toolingApiBuilders': Event handlers are in the wrong place, and should live in this project")
    }
}
