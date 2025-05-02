plugins {
    `java-library`
}

repositories { // <1>
    google()
    mavenCentral()
}

val customConfiguration by configurations.creating // <3>

dependencies { // <2>
    implementation("com.google.guava:guava:32.1.2-jre")
    testImplementation("junit:junit:4.13.2")
    customConfiguration("org.eclipse.jgit:org.eclipse.jgit:4.9.2.201712150930-r")

    constraints { // <4>
        api("org.apache.juneau:juneau-marshall:8.2.0")
    }
}
