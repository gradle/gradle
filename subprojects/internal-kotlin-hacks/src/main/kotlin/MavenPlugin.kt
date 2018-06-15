/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.api.internal.provider.Providers

import org.gradle.api.plugins.ExtensionContainer

import org.gradle.api.publish.Publication
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugin.devel.PluginDeclaration
import org.gradle.plugin.devel.plugins.Monadic.combine


class MavenPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        val pluginDevelopment = extensions.getByType<GradlePluginDevelopmentExtension>()

        // TODO: missing components.named("java")
        val javaComponent = Providers.of(components.getByName("java"))

        extensions.configure<PublishingExtension> {

            val inputs = combine(
                pluginDevelopment.isAutomatedPublishing,
                pluginDevelopment.plugins.asProvider(),
                javaComponent
            )

            val output = inputs.map { (isAutomated, plugins, component) ->

                if (isAutomated) {
                    val mainPublication = publications.create("pluginMaven", MavenPublication::class.java)
                    mainPublication.from(component)

                    plugins.map { pluginDeclaration ->
                        createMavenMarkerPublication(pluginDeclaration, mainPublication, publications)
                    } + mainPublication
                } else {
                    emptyList()
                }
            }

            publications.addAllLater(output)
        }
    }

    private
    fun createMavenMarkerPublication(
        plugin: PluginDeclaration,
        mainPublication: MavenPublication,
        publications: PublicationContainer
    ): Publication = TODO()
}


inline fun <reified T> ExtensionContainer.getByType() = getByType(T::class.java)


inline fun <reified T> ExtensionContainer.configure(noinline action: T.() -> Unit) = configure(T::class.java) { action(it) }
