package org.gradle.script.lang.kotlin.support

import org.gradle.api.Project
import org.gradle.script.lang.kotlin.KotlinBuildScript
import org.gradle.script.lang.kotlin.KotlinScriptHandler

abstract class KotlinBuildScriptSection(project: Project) : KotlinBuildScript(project) {

    override fun buildscript(configuration: KotlinScriptHandler.() -> Unit) {
        KotlinScriptHandler(project.buildscript).configuration()
    }
}
