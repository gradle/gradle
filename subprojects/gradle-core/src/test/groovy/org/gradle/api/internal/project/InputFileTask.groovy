package org.gradle.api.internal.project

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile

class InputFileTask extends DefaultTask {
    @InputFile
    File srcFile
}
