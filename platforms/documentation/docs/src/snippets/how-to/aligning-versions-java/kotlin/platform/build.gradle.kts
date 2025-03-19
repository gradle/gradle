plugins {
    id("java-platform")
}

group = "com.example"
version = "1.1"

dependencies {
    constraints {
        api("com.example:lib:1.1")
        api("com.example:utils:1.1")
        api("com.example:core:1.1")
    }
}
