// This file is located in /util
plugins {
    id("project-properties")
}

myProperties {
    propertyA = project.properties.get("propertyA") as String
    propertyB = "otherValue" // <6>
}
