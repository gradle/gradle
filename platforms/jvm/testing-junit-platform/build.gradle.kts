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
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":platform-jvm"))
    implementation(project(":language-java"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm-infrastructure"))
    implementation(project(":logging"))

    implementation(libs.junitPlatform)
}
