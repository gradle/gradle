// tag::basics[]
// Applies the Java plugin
plugins {
    id("java")
}

repositories {
    mavenCentral()
}

// Access to 'implementation' (contributed by the Java plugin) works here:
dependencies {
    implementation("org.apache.commons:commons-lang3:3.12.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher") // Add this if needed for runtime
}

// Add a custom configuration
configurations.create("customConfiguration")
// end::basics[]
/*
// tag::basics[]
// Type-safe accessors for 'customConfiguration' will NOT be available because it was created after the plugins block
dependencies {
    customConfiguration("com.google.guava:guava:32.1.2-jre") // ❌ Error: No type-safe accessor for 'customConfiguration'
}
// end::basics[]
*/

// tag::lazy[]
tasks.test {
    // lazy configuration
    useJUnitPlatform()
}

// Lazy reference
val testProvider: TaskProvider<Test> = tasks.test

testProvider {
    // lazy configuration
}

// Eagerly realized Test task, defeats configuration avoidance if done out of a lazy context
val test: Test = tasks.test.get()
// end::lazy[]

// tag::container[]
val mainSourceSetProvider: NamedDomainObjectProvider<SourceSet> = sourceSets.named("main")
// end::container[]

// tag::source[]
the<SourceSetContainer>()["main"].java.srcDir("src/main/java")
// end::source[]

// tag::property[]
val myProperty: String by project  // <1>
val myNullableProperty: String? by project // <2>
// end::property[]

// tag::extra[]
val myNewProperty by extra("initial value")  // <1>
val myOtherNewProperty by extra { "calculated initial value" }  // <2>

val myExtraProperty: String by extra  // <3>
val myExtraNullableProperty: String? by extra  // <4>
// end::extra[]

// tag::test-task[]
tasks {
    test {
        val reportType by extra("dev")  // <1>
        doLast {
            // Use 'suffix' for post-processing of reports
        }
    }

    register<Zip>("archiveTestReports") {
        val reportType: String by test.get().extra  // <2>
        archiveAppendix = reportType
        from(test.get().reports.html.outputLocation)
    }
}
// end::test-task[]

// tag::test-task-eager[]
tasks.test {
    doLast { /* ... */ }
}

val testReportType by tasks.test.get().extra("dev")  // <1>

tasks.create<Zip>("archiveTestsReports") {
    archiveAppendix = testReportType  // <2>
    from(test.reports.html.outputLocation)
}
// end::test-task-eager[]

// tag::write[]
extra["myNewProperty"] = "initial value"  // <1>

tasks.register("myTask") {
    doLast {
        println("Property: ${project.extra["myNewProperty"]}")  // <2>
    }
}
// end::write[]
