// Project properties can be accessed via delegation
val commandLineProjectProp: String by project
val gradlePropertiesProp: String by project
val systemProjectProp: String by project

tasks.register("printProps") {
    doLast {
        println(commandLineProjectProp)
        println(gradlePropertiesProp)
        println(systemProjectProp)
        println(System.getProperty("system"))
    }
}
