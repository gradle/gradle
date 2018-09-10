plugins {
    java
}

repositories {
    jcenter()
}

// tag::annotation-processing[]
dependencies {
    // The dagger compiler and its transitive dependencies will only be found on annotation processing classpath
    annotationProcessor("com.google.dagger:dagger-compiler:2.8")

    // And we still need the Dagger library on the compile classpath itself
    implementation("com.google.dagger:dagger:2.8")
}
// end::annotation-processing[]
