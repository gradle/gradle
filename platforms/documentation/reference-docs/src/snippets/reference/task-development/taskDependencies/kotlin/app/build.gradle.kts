// tag::app-build-script[]
plugins {
    id("application")                       // app is now a java application
}

application {
    mainClass.set("hello.HelloWorld")       // main class name required by the application plugin
}

dependencies {
    implementation(project(":some-logic"))  // dependency on some-logic
}
// end::app-build-script[]
