// tag::plugins-on-subprojects[]
plugins {
    // These plugins are not automatically applied.
    // They can be applied in subprojects as needed (in their respective build files).
    id("com.example.hello") version "1.0.0" apply false
    id("com.example.goodbye") version "1.0.0" apply false
}

allprojects {
    // Apply the common 'java' plugin to all projects (including the root)
    plugins.apply("java")
}

subprojects {
    // Apply the 'java-library' plugin to all subprojects (excluding the root)
    plugins.apply("java-library")
}
// end::plugins-on-subprojects[]
