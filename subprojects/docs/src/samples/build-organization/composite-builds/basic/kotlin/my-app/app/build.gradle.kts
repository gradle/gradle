plugins {
    application
}

group = "org.sample"
version = "1.0"

application {
    mainClass.set("org.sample.myapp.Main")
}

dependencies {
// tag::app_dependencies[]
    implementation("org.sample:number-utils:1.0")
    implementation("org.sample:string-utils:1.0")
// end::app_dependencies[]
}

repositories {
    mavenCentral()
}
