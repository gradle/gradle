plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::dependency-on-bom[]
dependencies {
    // import a BOM
    implementation(platform("org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE"))
    // define dependencies without versions
    implementation("com.google.code.gson:gson")
    implementation("dom4j:dom4j")
}
// end::dependency-on-bom[]

//Note: dom4j also brings in xml-apis as transitive dependency
tasks.register<Copy>("copyLibs") {
    from(configurations.compileClasspath)
    into(layout.buildDirectory.dir("libs"))
}
