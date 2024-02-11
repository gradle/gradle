plugins {
    `java-library`
}

repositories {
    ivy { url = uri(rootProject.layout.projectDirectory.dir("repo")) }
}
