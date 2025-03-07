plugins {
    id("application")
}

group = "org.sample"
version = "1.0"

repositories {
    mavenCentral()
}

application {
    mainClass = "org.sample.myapp.Main"
}

dependencies {
    implementation("com.squareup.okhttp:okhttp:2.7.5")
}
