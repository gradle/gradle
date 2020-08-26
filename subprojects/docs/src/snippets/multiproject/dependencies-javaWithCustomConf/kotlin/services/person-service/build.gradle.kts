plugins {
    id("myproject.java-conventions")
}

dependencies {
    implementation(project(":shared"))
    implementation(project(path = ":api", configuration = "spi"))

    testImplementation(project(":api"))
}
