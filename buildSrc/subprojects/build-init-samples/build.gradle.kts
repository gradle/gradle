dependencies {
    implementation("org.gradle.guides:gradle-guides-plugin")
    implementation("org.asciidoctor:asciidoctor-gradle-plugin") {
        because("This is a transitive dependency of 'gradle-guides-plugin' not declared there")
    }
}
