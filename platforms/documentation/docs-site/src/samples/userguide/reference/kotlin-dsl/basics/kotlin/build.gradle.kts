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

extra["myNullableProperty"] = null
// tag::extra[]
extra["myNewProperty"] = "initial value"  // <1>

val myExtraProperty = extra["myNewProperty"] as String  // <2>
val myExtraNullableProperty = extra["myNullableProperty"] as String?  // <3>
// end::extra[]

// tag::test-task[]
tasks {
    test {
        extra["reportType"] = "dev"  // <1>
        doLast {
            // Use 'reportType' for post-processing of reports
        }
    }

    register<Zip>("archiveTestReports") {
        from(test.map { it.reports.html.outputLocation })
        archiveAppendix = test.map { it.extra["reportType"] as String } // <2>
    }
}
// end::test-task[]
