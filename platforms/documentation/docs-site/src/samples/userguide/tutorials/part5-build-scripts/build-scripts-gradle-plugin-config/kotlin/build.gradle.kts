plugins {
    `java-gradle-plugin`
}

// tag::gradle-plugin-config[]
gradlePlugin {  // Define a custom plugin
    plugins.create("greeting") {  // Define `greeting` plugin using the `plugins.create` method
        id = "license.greeting" // Create plugin with the specified ID
        implementationClass = "license.LicensePlugin"   // and specified implementation class
    }
}
// end::gradle-plugin-config[]
