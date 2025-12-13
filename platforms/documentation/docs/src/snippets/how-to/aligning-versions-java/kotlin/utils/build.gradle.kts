plugins {
    id("java-library")
}

group = "com.example"
version = "1.1"

dependencies {
    api(platform(project(":platform")))
}
