plugins {
    `java-library`
}

repositories { // <1>
    google() // <2>
    mavenCentral()
}

dependencies { // <3>
    implementation("com.google.guava:guava:32.1.2-jre") // <4>
    testImplementation("junit:junit:4.13.2")
}
