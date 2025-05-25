plugins {
    id("java-library")
}

group = "com.example"
version = "1.1"

dependencies {
    implementation(platform(project(":platform")))
}
