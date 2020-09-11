dependencies {
    compileOnly("com.gradle:gradle-enterprise-gradle-plugin")

    implementation(project(":basics"))
    implementation(project(":documentation"))

    implementation("me.champeau.gradle:jmh-gradle-plugin")
    implementation("org.jsoup:jsoup")
}
