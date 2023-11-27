package org.gradle.sample

import org.gradle.api.Plugin
import org.gradle.api.Project

class HelloWorldPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.file('input.txt').text = 'Input'
        project.task('cacheableTask', type: MyCacheableTask) {
            inputFile = project.file('input.txt')
            outputFile = project.file("${project.buildDir}/output.txt")
        }
    }
}
