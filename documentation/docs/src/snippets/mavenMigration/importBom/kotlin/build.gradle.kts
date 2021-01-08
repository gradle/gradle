// tag::plugins[]
plugins {
    java
}
// end::plugins[]

repositories {
    mavenCentral()
}

// tag::bom[]
dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE"))  // <1>

    implementation("com.google.code.gson:gson")  // <2>
    implementation("dom4j:dom4j")
}
// end::bom[]
