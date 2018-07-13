dependencies {
    compile("commons-lang:commons-lang:2.6")
    compile("com.google.guava:guava-jdk5:14.0.1")
    compile("org.asciidoctor:asciidoctorj-pdf:1.5.0-alpha.11") {
        exclude(module="asciidoctorj")
    }
    compile("org.asciidoctor:asciidoctorj:1.5.5")
    compile("org.asciidoctor:asciidoctor-gradle-plugin:1.5.3")
}
