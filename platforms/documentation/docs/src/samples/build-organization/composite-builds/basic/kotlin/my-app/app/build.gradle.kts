// tag::app_dependencies[]
plugins {
    id("application")
}

application {
    mainClass = "org.sample.myapp.Main"
}

dependencies {
    implementation("org.sample:number-utils:1.0")
    implementation("org.sample:string-utils:1.0")
}
// end::app_dependencies[]

group = "org.sample"
version = "1.0"

repositories {
    mavenCentral()
}
