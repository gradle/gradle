plugins {
    id("myproject.java-conventions")
    `java-library`
}

dependencies {
    implementation(project(":list"))
}
