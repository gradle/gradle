// This file is located in /util
plugins {
    id("project-properties")
}

myProperties {
    propertyA = providers.gradleProperty("propertyA")
    propertyB = "otherValue" // <6>
}
