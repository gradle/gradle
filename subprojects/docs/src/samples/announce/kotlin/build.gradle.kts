// tag::use-plugin[]
plugins {
    announce
}
// end::use-plugin[]

// tag::announce-plugin-conf[]
announce {
    username = "myId"
    password = "myPassword"
}
// end::announce-plugin-conf[]

// tag::announce-usage[]
val helloWorld by tasks.registering {
    doLast {
        println("Hello, world!")
    }
}

helloWorld {
    doLast {
        announce.announce("helloWorld completed!", "twitter")
        announce.announce("helloWorld completed!", "local")
    }
}
// end::announce-usage[]
