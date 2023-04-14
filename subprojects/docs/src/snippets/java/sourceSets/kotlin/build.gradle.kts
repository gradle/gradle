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
    "intTestImplementation"("junit:junit:4.13")
    "intTestRuntimeOnly"("org.ow2.asm:asm:7.1")
}

// tag::jar[]
tasks.register<Jar>("intTestJar") {
    from(sourceSets["intTest"].output)
}
// end::jar[]

// tag::javadoc[]
tasks.register<Javadoc>("intTestJavadoc") {
    source(sourceSets["intTest"].allJava)
    classpath = sourceSets["intTest"].compileClasspath
}
// end::javadoc[]

// tag::test[]
tasks.register<Test>("intTest") {
    testClassesDirs = sourceSets["intTest"].output.classesDirs
    classpath = sourceSets["intTest"].runtimeClasspath
}
// end::test[]

tasks.named("test") {
    dependsOn("intTest")
}

tasks.named("build") {
    dependsOn("intTestJavadoc")
}
