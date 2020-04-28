plugins {
    id("com.example.library")
}

dependencies {
    implementation(project(":internal-module"))
}
