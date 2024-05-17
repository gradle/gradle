import org.gradle.api.internal.component.SoftwareComponentInternal

// tag::use-plugin[]
plugins {
    // A Java Library
    `java-library`
    // which produces test fixtures
    `java-test-fixtures`
    // and is published
    `maven-publish`
}
// end::use-plugin[]

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// tag::test_fixtures_deps[]
dependencies {
    testImplementation("junit:junit:4.13")

    // API dependencies are visible to consumers when building
    testFixturesApi("org.apache.commons:commons-lang3:3.9")

    // Implementation dependencies are not leaked to consumers when building
    testFixturesImplementation("org.apache.commons:commons-text:1.6")
}
// end::test_fixtures_deps[]

// tag::publishing_test_fixtures[]
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
// end::publishing_test_fixtures[]

// tag::disable-test-fixtures-publishing[]
val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
javaComponent.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
// end::disable-test-fixtures-publishing[]

tasks.create("usages") {
    val javaComponentUsages = (components["java"] as SoftwareComponentInternal).usages.map { it.name }
    doLast {
        javaComponentUsages.forEach { println(it) }
    }
}
