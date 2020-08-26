
plugins {
    application // <1>
}

repositories {
    jcenter() // <2>
}

dependencies {
    implementation("com.google.guava:guava:29.0-jre") // <3>

    testImplementation("junit:junit:4.13") // <4>
}

application {
    mainClass.set("demo.App") // <5>
}
