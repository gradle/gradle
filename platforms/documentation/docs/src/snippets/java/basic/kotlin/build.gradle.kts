// tag::apply-java-plugin[]
plugins {
    `java-library`
}

// tag::java-extension[]
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
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
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()

    maxHeapSize = "1G"

    testLogging {
        events("passed")
    }
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
val intTestRuntimeOnly by configurations.getting

configurations["intTestRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

dependencies {
    intTestImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    intTestRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
tasks.test {
    val skipTestsProvider = providers.gradleProperty("mySkipTests")
    onlyIf("mySkipTests property is not set") {
        !skipTestsProvider.isPresent()
    }
}
// end::skip-tests-condition[]

// tag::java-compiler-options[]
tasks.compileJava {
    options.isIncremental = true
    options.isFork = true
    options.isFailOnError = false
}
// end::java-compiler-options[]

// tag::java-release-flag[]
tasks.compileJava {
    options.release = 7
}
// end::java-release-flag[]

// tag::integ-test-task[]
val integrationTest = task<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"

    testClassesDirs = sourceSets["intTest"].output.classesDirs
    classpath = sourceSets["intTest"].runtimeClasspath
    shouldRunAfter("test")

    useJUnitPlatform()

    testLogging {
        events("passed")
    }
}

tasks.check { dependsOn(integrationTest) }
// end::integ-test-task[]

// tag::defining-custom-javadoc-task[]
tasks.register<Javadoc>("testJavadoc") {
    source = sourceSets.test.get().allJava
}
// end::defining-custom-javadoc-task[]
