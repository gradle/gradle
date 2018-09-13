// tag::apply-java-plugin[]
plugins {
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

version = "1.2.1"
// end::apply-java-plugin[]

// tag::java-dependency-mgmt[]
repositories {
    mavenCentral()
}

dependencies {
    implementation("org.hibernate:hibernate-core:3.6.7.Final")
}
// end::java-dependency-mgmt[]

// tag::java-basic-test-config[]
dependencies {
    testImplementation("junit:junit:4.12")
}

tasks.getByName<Test>("test") {
    useJUnit()

    maxHeapSize = "1G"
}
// end::java-basic-test-config[]

// tag::practical-integ-test-source-set[]
sourceSets {
    create("intTest") {
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
    }
}

val intTestImplementation = configurations.getByName("intTestImplementation") {
    extendsFrom(configurations.implementation)
}

configurations["intTestRuntimeOnly"].extendsFrom(configurations.runtimeOnly)

dependencies {
    intTestImplementation("junit:junit:4.12")
}
// end::practical-integ-test-source-set[]

// tag::using-custom-doclet[]
val asciidoclet = configurations.create("asciidoclet")

dependencies {
    asciidoclet("org.asciidoctor:asciidoclet:1.+")
}

task("configureJavadoc") {
    doLast {
        val javadoc = tasks.getByName<Javadoc>("javadoc")
        javadoc.options.doclet = "org.asciidoctor.Asciidoclet"
        javadoc.options.docletpath = asciidoclet.files.toList()
    }
}

tasks.getByName<Javadoc>("javadoc") {
    dependsOn("configureJavadoc")
}
// end::using-custom-doclet[]


// tag::skip-tests-condition[]
tasks["test"].onlyIf { !project.hasProperty("mySkipTests") }
// end::skip-tests-condition[]

// tag::java-compiler-options[]
tasks.getByName<JavaCompile>("compileJava") {
    options.isIncremental = true
    options.isFork = true
    options.isFailOnError = false
}
// end::java-compiler-options[]

// tag::integ-test-task[]
val integrationTest = task("integrationTest", Test::class) {
    description = "Runs integration tests."
    group = "verification"

    testClassesDirs = sourceSets["intTest"].output.classesDirs
    classpath = sourceSets["intTest"].runtimeClasspath
    shouldRunAfter("test")
}

tasks["check"].dependsOn(integrationTest)
// end::integ-test-task[]

// tag::defining-sources-jar-task[]
task("sourcesJar", Jar::class) {
    classifier = "sources"
    from(sourceSets["main"].allJava)
}
// end::defining-sources-jar-task[]


// tag::defining-custom-javadoc-task[]
task("testJavadoc", Javadoc::class) {
    source = sourceSets["test"].allJava
}
// end::defining-custom-javadoc-task[]
