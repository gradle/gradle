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
tasks.test {
    useJUnit {
        includeCategories("org.gradle.junit.CategoryA")
        excludeCategories("org.gradle.junit.CategoryB")
    }
}
// end::test-categories[]
