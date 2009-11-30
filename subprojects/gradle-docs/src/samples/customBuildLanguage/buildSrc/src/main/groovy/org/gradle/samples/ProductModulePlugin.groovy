package org.gradle.samples

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ProjectPluginsContainer

/**
 * A plugin which configures a product module project. Each product module is assembled into one or more products.
 */
class ProductModulePlugin implements Plugin {

    void use(Project project, ProjectPluginsContainer projectPluginsHandler) {
        project.configure(project) {
            usePlugin 'java'
            repositories {
                mavenCentral()
            }
            archivesBaseName = "some-company-${name.replaceAll('(\\p{Upper})', '-$1').toLowerCase()}"
        }
    }
}