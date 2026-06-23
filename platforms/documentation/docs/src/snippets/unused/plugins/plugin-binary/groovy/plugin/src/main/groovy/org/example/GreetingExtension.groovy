package org.example

import org.gradle.api.provider.Property
import org.gradle.api.file.RegularFileProperty

abstract class GreetingExtension {
    abstract Property<String> getMessage()          // <1>
    abstract RegularFileProperty getOutputFile()    // <2>
}
