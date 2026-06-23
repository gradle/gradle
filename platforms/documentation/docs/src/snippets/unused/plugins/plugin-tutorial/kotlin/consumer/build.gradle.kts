// tag::build[]
plugins {
    id("java")
    // end::build[]
    /*
    // tag::build[]
    id("org.example.slack")
    // end::build[]
     */
    // tag::build[]
}

// end::build[]
/*
// tag::repo[]
plugins {
    id("java")
    id("org.example.slack") version "1.0.0"
}
// end::repo[]
*/
// tag::build[]

repositories {
    mavenCentral()
}

// end::build[]
/*
// tag::build[]
slack {
    token.set(System.getenv("SLACK_TOKEN"))
    channel.set("#social")
    message.set("Hello from consumer via composite build!")
}
// end::build[]
*/
