// tag::apply-java-plugin[]
plugins {
    `java-library`
}

// tag::java-extension[]
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
// end::java-extension[]

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

tasks.test {
    useJUnit()

    maxHeapSize = "1G"
}
// end::java-basic-test-config[]

// tag::practical-integ-test-source-set[]
sourceSets {
    create("intTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val intTestImplementation by configurations.getting {
    extendsFrom(configurations.implementation.get())
}

configurations["intTestRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

dependencies {
    intTestImplementation("junit:junit:4.12")
}
// end::practical-integ-test-source-set[]

// tag::using-custom-doclet[]
val asciidoclet by configurations.creating

dependencies {
    asciidoclet("org.asciidoctor:asciidoclet:1.+")
}

tasks.register("configureJavadoc") {
    doLast {
        tasks.javadoc {
            options.doclet = "org.asciidoctor.Asciidoclet"
            options.docletpath = asciidoclet.files.toList()
        }
    }
}

tasks.javadoc {
    dependsOn("configureJavadoc")
}
// end::using-custom-doclet[]


// tag::skip-tests-condition[]
tasks.test { onlyIf { !project.hasProperty("mySkipTests") } }
// end::skip-tests-condition[]

// tag::java-compiler-options[]
tasks.compileJava {
    options.isIncremental = true
    options.isFork = true
    options.isFailOnError = false
}
// end::java-compiler-options[]

// tag::integ-test-task[]
val integrationTest = task<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"

    testClassesDirs = sourceSets["intTest"].output.classesDirs
    classpath = sourceSets["intTest"].runtimeClasspath
    shouldRunAfter("test")
}

tasks.check { dependsOn(integrationTest) }
// end::integ-test-task[]

// tag::defining-custom-javadoc-task[]
tasks.register<Javadoc>("testJavadoc") {
    source = sourceSets.test.get().allJava
}
// end::defining-custom-javadoc-task[]
