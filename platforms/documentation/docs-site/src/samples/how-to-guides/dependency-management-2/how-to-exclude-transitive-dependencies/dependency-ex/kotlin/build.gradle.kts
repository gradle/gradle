plugins {
    id("application")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("commons-beanutils:commons-beanutils:1.9.4") {
        exclude(group = "commons-collections", module = "commons-collections")
    }
}
