// tag::extraProperties[]
plugins {
    id("java-library")
}

val springVersion by extra("3.1.0.RELEASE")
val emailNotification by extra { "build@master.org" }

sourceSets.all { extra["purpose"] = null }

sourceSets {
    main {
        extra["purpose"] = "production"
    }
    test {
        extra["purpose"] = "test"
    }
    create("plugin") {
        extra["purpose"] = "production"
    }
}

tasks.register("printProperties") {
    doLast {
        println(springVersion)
        println(emailNotification)
        sourceSets.matching { it.extra["purpose"] == "production" }.forEach { println(it.name) }
    }
}
// end::extraProperties[]
