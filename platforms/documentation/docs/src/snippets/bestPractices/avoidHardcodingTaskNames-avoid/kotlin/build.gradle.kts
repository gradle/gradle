// tag::avoid-this[]
plugins {
    id("java-library")
}

tasks.named<JavaCompile>("compileJava") { // <1>
    sourceCompatibility = "17"
}

tasks.named<Test>("test") { // <2>
    useJUnit()
}
// end::avoid-this[]

dependencies {
    testImplementation("junit:junit:4.13.2")
}
