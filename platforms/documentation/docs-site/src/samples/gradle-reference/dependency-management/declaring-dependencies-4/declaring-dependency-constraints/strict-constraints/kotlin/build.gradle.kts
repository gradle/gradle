dependencies {
    constraints {
        implementation("com.google.guava:guava") {
            version {
                strictly("33.1.0-jre")
            }
            because("avoid older versions with known issues")
        }
    }
}
