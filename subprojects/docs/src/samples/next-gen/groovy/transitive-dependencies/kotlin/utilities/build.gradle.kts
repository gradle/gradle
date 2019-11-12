plugins {
    `java-library`
    groovy
}

repositories {
    jcenter()
}

dependencies {
    api(project(":list"))
    implementation("org.codehaus.groovy:groovy-all:2.5.7")
}
