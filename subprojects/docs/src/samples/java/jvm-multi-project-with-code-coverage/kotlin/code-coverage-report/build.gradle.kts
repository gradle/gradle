plugins {
    id("myproject.jacoco-aggregation")
}

dependencies {
    implementation(project(":application"))
}
