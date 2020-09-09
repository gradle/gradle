// tag::plugins[]
plugins {
    id("myproject.java-conventions")
    // com.example.plugin.greeting is implemented in the buildSrc project that has myproject.java-conventions applied as well
    id("com.example.plugin.greeting")
    id("application")
}
// end::plugins[]

dependencies {
    implementation(project(":utilities"))
}

application {
    mainClass.set("org.gradle.sample.app.Main")
}
