dependencies {
    // import a BOM
    implementation(platform("org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE"))
    // define dependencies without versions
    implementation("com.google.code.gson:gson")
    implementation("dom4j:dom4j")
}
