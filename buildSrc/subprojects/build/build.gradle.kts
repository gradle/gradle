dependencies {
    api("com.google.guava:guava:26.0-jre")
    api("org.asciidoctor:asciidoctor-gradle-plugin:1.5.9.2")
    implementation("org.asciidoctor:asciidoctorj:1.5.8.1")
    implementation("org.jruby:jruby-complete:9.2.5.0")
    implementation(project(":buildPlatform"))
    implementation("commons-lang:commons-lang:2.6")
    implementation("org.asciidoctor:asciidoctorj-pdf:1.5.0-alpha.16")
    implementation("com.github.javaparser:javaparser-core")
}
