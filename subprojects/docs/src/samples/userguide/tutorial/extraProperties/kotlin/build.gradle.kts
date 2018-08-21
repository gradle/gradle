// tag::extraProperties[]
plugins {
    java
}
// end::extraProperties[]


// tag::taskProperties[]
task("myTask") {
    extra["myProperty"] = "myValue"
}

task("printTaskProperties") {
    doLast {
        println(tasks["myTask"].extra["myProperty"])
    }
}
// end::taskProperties[]


// tag::extraProperties[]

val springVersion by extra("3.1.0.RELEASE")
val emailNotification by extra("build@master.org")

sourceSets.all { extra["purpose"] = null }

(sourceSets) {
    "main" {
        this as ExtensionAware
        extra["purpose"] = "production"
    }
    "test" {
        this as ExtensionAware
        extra["purpose"] = "test"
    }
    create("plugin") {
        this as ExtensionAware
        extra["purpose"] = "production"
    }
}

task("printProperties") {
    doLast {
        println(springVersion)
        println(emailNotification)
        sourceSets.matching { (it as ExtensionAware).extra["purpose"] == "production" }.forEach { println(it.name) }
    }
}
// end::extraProperties[]
