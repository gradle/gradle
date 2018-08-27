plugins {
    `java-library`
    id("gradlebuild.classycle")
}

dependencies {
    api(project(":cli"))
    api(library("jsr305"))
    implementation("commons-lang:commons-lang:2.6")
}
