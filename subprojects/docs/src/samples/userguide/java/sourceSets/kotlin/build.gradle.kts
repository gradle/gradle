plugins {
    java
}

repositories {
    mavenCentral()
}

sourceSets {
    create("intTest")
}

dependencies {
    "intTestImplementation"("junit:junit:4.12")
    "intTestRuntimeOnly"("org.ow2.asm:asm-all:4.0")
}

// tag::jar[]
task<Jar>("intTestJar") {
    from(sourceSets["intTest"].output)
}
// end::jar[]

// tag::javadoc[]
task<Javadoc>("intTestJavadoc") {
    source(sourceSets["intTest"].allJava)
}
// end::javadoc[]

// tag::test[]
task<Test>("intTest") {
    testClassesDirs = sourceSets["intTest"].output.classesDirs
    classpath = sourceSets["intTest"].runtimeClasspath
}
// end::test[]
