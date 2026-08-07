// tag::create-platform[]
plugins {
    `java-platform`
}

dependencies {
    constraints {
        api("com.google.code.gson:gson:2.10.1")
        api("org.apache.commons:commons-lang3:3.14.0")
        api("org.slf4j:slf4j-api:2.0.9")
    }
}
// end::create-platform[]

tasks.register("showConstraints") {
    doLast {
        val cfg = configurations["apiElements"]
        println("Constraints published by this platform:")
        cfg.allDependencyConstraints.forEach {
            println("  ${it.group}:${it.name}:${it.version}")
        }
    }
}
