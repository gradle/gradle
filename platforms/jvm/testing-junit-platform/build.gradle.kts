plugins {
    id("gradlebuild.distribution.api-java")
}


description = """Support classes used to run tests with the JUnit Platform testing framework.
This project is separate from :testing-jvm-infrastructure since it requires junit-platform which itself requires Java 8+.
This project should only be used by :testing-jvm-infrastructure, however it is not depended upon directly.
Instead :testing-jvm-infrastructure loads classes from this project via reflection due to the above noted Java version issue.
We make sure to include this subproject as a runtime dependency in :distributions-core to ensure we include it with the Gradle distribution.
"""

dependencies {
    api(projects.testingBaseInfrastructure)
    api(projects.time)
    api(projects.baseServices)
    api(projects.messaging)
    api(projects.testingJvmInfrastructure)

    api(libs.jspecify)

    implementation(projects.stdlibJavaExtensions)

    compileOnly(libs.junitPlatform) {
        because("The actual version is provided by the user on the testRuntimeClasspath")
    }
    compileOnly(libs.junitPlatformEngine) {
        because("The actual version is provided by the user on the testRuntimeClasspath")
    }

    implementation(libs.jsr305)
}
