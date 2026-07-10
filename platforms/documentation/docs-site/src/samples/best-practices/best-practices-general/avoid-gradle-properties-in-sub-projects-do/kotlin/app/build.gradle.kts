// This file is located in /app
plugins { // <4>
    id("project-properties")
}

myProperties { // <5>
    propertyA = providers.gradleProperty("propertyA")
    propertyB = providers.gradleProperty("propertyB")
}
