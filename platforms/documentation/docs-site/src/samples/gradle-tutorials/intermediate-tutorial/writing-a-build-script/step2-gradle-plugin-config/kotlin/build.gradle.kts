gradlePlugin {  // Define a custom plugin
    val greeting by plugins.creating {  // Define `greeting` plugin using the `plugins.creating` method
        id = "license.greeting" // Create plugin with the specified ID
        implementationClass = "license.LicensePlugin"   // and specified implementation class
    }
}
