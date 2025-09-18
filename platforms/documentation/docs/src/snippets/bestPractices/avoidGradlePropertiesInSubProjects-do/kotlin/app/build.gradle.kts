// This file is located in /app
plugins { // <4>
    id("project-properties")
}

myProperties { // <5>
    propertyA = project.properties.get("propertyA") as String
    propertyB = project.properties.get("propertyB") as String
}
