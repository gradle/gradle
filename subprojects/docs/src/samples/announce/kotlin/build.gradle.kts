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
task("helloWorld") {
    doLast {
        println("Hello, world!")
    }
}

tasks.getByName("helloWorld").doLast {
    announce.announce("helloWorld completed!", "twitter")
    announce.announce("helloWorld completed!", "local")
}
// end::announce-usage[]
