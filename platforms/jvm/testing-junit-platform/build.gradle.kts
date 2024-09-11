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
    api(projects.stdlibJavaExtensions)
    api(projects.testingBaseInfrastructure)
    api(projects.time)
    api(projects.baseServices)
    api(projects.messaging)
    api(projects.testingJvmInfrastructure)

    api(libs.junitPlatform)
    api(libs.junitPlatformEngine)

    implementation(libs.jsr305)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
