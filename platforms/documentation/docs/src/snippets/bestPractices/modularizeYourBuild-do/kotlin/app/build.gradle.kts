// tag::do-this[]
plugins {
    application // <2>
}

dependencies { // <3>
    implementation(project(":util-guava"))
    implementation(project(":util-commons"))
}

application {
    mainClass = "org.example.Main"
}
// end::do-this[]
