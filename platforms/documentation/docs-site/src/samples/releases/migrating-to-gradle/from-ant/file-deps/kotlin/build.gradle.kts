repositories {
    flatDir {
        name = "libs dir"
        dir(file("libs"))  // <1>
    }
}

dependencies {
    implementation(files("libs/our-custom.jar"))  // <2>
    implementation(":awesome-framework:2.0")     // <3>
    implementation(":utility-library:1.0")  // <3>
}
