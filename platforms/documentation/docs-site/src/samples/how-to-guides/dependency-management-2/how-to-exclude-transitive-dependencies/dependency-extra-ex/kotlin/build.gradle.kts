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
    implementation("com.opencsv:opencsv:4.6") {
        exclude(group = "commons-collections", module = "commons-collections")
        exclude(group = "org.apache.commons", module = "commons-collections4") // Watch out for other transitive dependency creep
    }
}
