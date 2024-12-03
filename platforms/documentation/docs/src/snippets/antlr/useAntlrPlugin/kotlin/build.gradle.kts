// tag::use-plugin[]
plugins {
    antlr
}
// end::use-plugin[]

// tag::declare-dependency[]
repositories {
    mavenCentral()
}

dependencies {
    antlrTool("org.antlr:antlr:3.5.2")     // use ANTLR version 3
    api("org.antlr:antlr-runtime:3.5.2")
    // antlrTool("org.antlr:antlr4:4.5")   // use ANTLR version 4
    // api("org.antlr:antlr4-runtime:4.5")
// end::declare-dependency[]
    testImplementation("junit:junit:4.13")
// tag::declare-dependency[]
}
// end::declare-dependency[]

// tag::generate-grammar-settings[]
tasks.generateGrammarSource {
    maxHeapSize = "64m"
    arguments = arguments + listOf("-visitor", "-long-messages")
}
// end::generate-grammar-settings[]
