package org.example

import org.gradle.api.provider.Property
import org.gradle.api.file.RegularFileProperty

abstract class GreetingExtension {
    abstract val message: Property<String>          // <1>
    abstract val outputFile: RegularFileProperty    // <2>
}
