plugins {
    id("myproject.java-conventions")
    `java-library`
}

dependencies {
    api(project(":list"))
}
