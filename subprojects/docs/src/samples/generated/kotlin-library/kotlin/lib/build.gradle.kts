
plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.72" // <1>

    `java-library` // <2>
}

repositories {
    jcenter() // <3>
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom")) // <4>

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8") // <5>

    testImplementation("org.jetbrains.kotlin:kotlin-test") // <6>

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit") // <7>
}
