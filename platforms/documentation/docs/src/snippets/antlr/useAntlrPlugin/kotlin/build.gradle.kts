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
    antlr("org.antlr:antlr:3.5.2")   // use ANTLR version 3
    // antlr("org.antlr:antlr4:4.5") // use ANTLR version 4
// end::declare-dependency[]
    testImplementation("junit:junit:4.13")
// tag::declare-dependency[]
}
// end::declare-dependency[]

// tag::generate-grammar-settings[]
tasks.generateGrammarSource {
    maxHeapSize = "64m"
    arguments.addAll(listOf("-visitor", "-long-messages"))
}
// end::generate-grammar-settings[]
