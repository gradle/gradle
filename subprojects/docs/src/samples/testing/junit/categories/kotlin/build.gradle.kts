plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    testCompile("junit:junit:4.12")
}

// tag::test-categories[]
tasks.named<Test>("test") {
    useJUnit {
        includeCategories("org.gradle.junit.CategoryA")
        excludeCategories("org.gradle.junit.CategoryB")
    }
}
// end::test-categories[]
