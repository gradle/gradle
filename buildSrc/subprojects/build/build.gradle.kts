dependencies {
    implementation(project(":buildPlatform"))
    implementation("commons-lang:commons-lang:2.6")
    api("com.google.guava:guava:26.0-jre")
    implementation("org.asciidoctor:asciidoctorj-pdf:1.5.0-alpha.11")
    implementation("org.asciidoctor:asciidoctorj:1.5.5")
    implementation("org.asciidoctor:asciidoctor-gradle-plugin:1.5.3")
    implementation("com.github.javaparser:javaparser-core")
}
