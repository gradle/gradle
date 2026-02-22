plugins {
    ear
}

dependencies {
    // Include WAR module in EAR
    deploy(project(":war", "war"))

    // Shared libraries placed in EAR/lib directory
    earlib("org.apache.commons:commons-lang3:3.14.0")
}
