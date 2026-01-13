// tag::app_dependencies[]
// tag::plugin_dependencies[]
plugins {
    id("application")
    id("com.example.hello") // from the included build in pluginManagement
}
// end::plugin_dependencies[]

application {
    mainClass = "org.sample.myapp.Main"
}

dependencies {
    // Substituted by the included build "my-utils"
    implementation("org.sample:number-utils:1.0")
    implementation("org.sample:string-utils:1.0")
}
// end::app_dependencies[]

group = "org.sample"
version = "1.0"

repositories {
    mavenCentral()
}

println("propertiesFileMessage = ${findProperty("propertiesFileMessage")}")
println("systemMessage = ${System.getProperty("systemMessage")}")
println("propertiesMessage = ${findProperty("propertiesMessage")}")
