package org.gradle.api.internal.project

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.util.HelperUtil

class InputFileTask extends DefaultTask {
    @InputFile
    File srcFile

    def InputFileTask() {
        super(HelperUtil.createRootProject(), 'task')
    }
}
