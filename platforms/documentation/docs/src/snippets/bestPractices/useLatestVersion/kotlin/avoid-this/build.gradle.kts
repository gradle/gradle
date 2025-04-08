// tag::avoid-this[]
plugins {
    id("java")
    id("com.diffplug.spotless").version("6.25.0")// <1>
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.commons:commons-lang3:3.12.0")
}
// end::avoid-this[]
