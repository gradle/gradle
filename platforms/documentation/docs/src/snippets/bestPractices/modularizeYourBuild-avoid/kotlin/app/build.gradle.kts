// tag::avoid-this[]
plugins {
    application // <2>
}

dependencies {
    implementation("com.google.guava:guava:31.1-jre") // <3>
    implementation("commons-lang:commons-lang:2.6")
}

application {
    mainClass = "org.example.Main"
}
// end::avoid-this[]
