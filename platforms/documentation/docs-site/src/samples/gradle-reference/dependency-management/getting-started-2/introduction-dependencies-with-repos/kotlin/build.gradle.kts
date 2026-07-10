repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("com.google.guava:guava:32.1.2-jre") // <2>
    api("org.apache.juneau:juneau-marshall:8.2.0")      // <3>
}
