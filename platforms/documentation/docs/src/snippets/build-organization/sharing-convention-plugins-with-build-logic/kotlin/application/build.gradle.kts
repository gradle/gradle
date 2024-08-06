// tag::plugins[]
plugins {
    id("myproject.java-conventions")
    // myproject.greeting is implemented in the buildSrc project that has myproject.java-conventions applied as well
    id("myproject.greeting")
    id("application")
}
// end::plugins[]

dependencies {
    implementation(project(":utilities"))
}

application {
    mainClass = "org.gradle.sample.app.Main"
}
