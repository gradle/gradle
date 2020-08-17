package org.gradle.samples

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * A plugin which configures a product module project. Each product module is assembled into one or more products.
 */
class ProductModulePlugin implements Plugin<Project> {
    void apply(Project project) {
        project.configure(project) {
            apply plugin: 'java-library'
            repositories {
                mavenCentral()
            }
            archivesBaseName = "some-company-${name.replaceAll('(\\p{Upper})', '-$1').toLowerCase()}"
        }
    }
}
