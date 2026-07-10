plugins {
    java
}

repositories {
    mavenCentral()
}

// tag::annotation-processing[]
dependencies {
    // The dagger compiler and its transitive dependencies will only be found on annotation processing classpath
    annotationProcessor("com.google.dagger:dagger-compiler:2.44")

    // And we still need the Dagger library on the compile classpath itself
    implementation("com.google.dagger:dagger:2.44")
}
// end::annotation-processing[]
