package org.gradle.script.lang.kotlin.provider

import org.gradle.scripts.ScriptingLanguage

class KotlinScriptingLanguage : ScriptingLanguage {

    override fun getExtension() = ".gradle.kts"

    override fun getProvider() = KotlinScriptPluginFactory::class.qualifiedName
}
