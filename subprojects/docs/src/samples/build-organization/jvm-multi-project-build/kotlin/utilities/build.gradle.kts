plugins {
    id("myproject.jvm-conventions")
    `java-library`
}

dependencies {
    api(project(":list"))
}
