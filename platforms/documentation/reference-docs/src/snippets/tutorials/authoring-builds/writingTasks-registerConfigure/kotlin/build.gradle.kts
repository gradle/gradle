plugins {
    id("java")
}

// tag::register-configure[]
tasks.register("task1"){  // <1>
    println("REGISTER TASK1: This is executed during the configuration phase")
}

tasks.named("task1"){  // <2>
    println("NAMED TASK1: This is executed during the configuration phase")
    doFirst {
        println("NAMED TASK1 - doFirst: This is executed during the execution phase")
    }
    doLast {
        println("NAMED TASK1 - doLast: This is executed during the execution phase")
    }
}
// end::register-configure[]
