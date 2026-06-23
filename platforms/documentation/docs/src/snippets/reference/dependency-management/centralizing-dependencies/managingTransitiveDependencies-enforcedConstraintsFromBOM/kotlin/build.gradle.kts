plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::dependency-on-bom[]
dependencies {
    // import a BOM. The versions used in this file will override any other version found in the graph
    implementation(enforcedPlatform("org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE"))

    // define dependencies without versions
    implementation("com.google.code.gson:gson")
    implementation("dom4j:dom4j")

    // this version will be overridden by the one found in the BOM
    implementation("org.codehaus.groovy:groovy:1.8.6")
}
// end::dependency-on-bom[]

//Note: dom4j also brings in xml-apis as transitive dependency
tasks.register<Copy>("copyLibs") {
    from(configurations.compileClasspath)
    into(layout.buildDirectory.dir("libs"))
}
