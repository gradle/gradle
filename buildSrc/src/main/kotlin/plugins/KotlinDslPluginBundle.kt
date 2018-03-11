package plugins

import com.gradle.publish.PluginBundleExtension
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.WriteProperties
import org.gradle.api.tasks.testing.Test
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension


class KotlinDslPlugin(private val name: String) : Named {
    override fun getName() = name
    lateinit var displayName: String
    lateinit var id: String
    lateinit var implementationClass: String
}


open class KotlinDslPluginBundle : Plugin<Project> {

    private
    lateinit var kotlinDslPlugins: DomainObjectCollection<KotlinDslPlugin>

    override fun apply(project: Project): Unit = project.run {

        plugins.apply(KotlinDslModule::class.java)
        plugins.apply("maven-publish")
        plugins.apply("java-gradle-plugin")
        plugins.apply("com.gradle.plugin-publish")

        testsDependOnCustomInstallation()

        kotlinDslPlugins = container(KotlinDslPlugin::class.java)
        extensions.add("kotlinDslPlugins", kotlinDslPlugins)

        configureGradlePluginDevelopmentPlugins()

        workAroundTestKitWithPluginClassPathIssues()
    }

    private
    fun Project.testsDependOnCustomInstallation() =
        tasks.withType(Test::class.java).all {
            it.dependsOn(rootProject.tasks.getByName("customInstallation"))
        }

    private
    fun Project.configureGradlePluginDevelopmentPlugins() {

        pluginBundle {
            tags = listOf("Kotlin", "DSL")
            website = "https://github.com/gradle/kotlin-dsl"
            vcsUrl = "https://github.com/gradle/kotlin-dsl"
        }

        afterEvaluate {

            pluginBundle {
                mavenCoordinates.artifactId = base.archivesBaseName
            }

            kotlinDslPlugins.all { plugin ->

                gradlePlugin {
                    plugins {
                        it.create(plugin.name) {
                            it.id = plugin.id
                            it.implementationClass = plugin.implementationClass
                        }
                    }
                }

                pluginBundle {
                    plugins {
                        it.create(plugin.name) {
                            it.id = plugin.id
                            it.displayName = plugin.displayName
                            it.description = plugin.displayName
                        }
                    }
                }
            }
        }
    }

    // TODO Remove work around for TestKit withPluginClassPath() issues
    // See TODO ISSUE NUMBER
    // Also see AbstractPluginTest
    private
    fun Project.workAroundTestKitWithPluginClassPathIssues() {

        publishing {
            repositories {
                it.maven {
                    it.name = "test"
                    it.url = uri("$buildDir/repository")
                }
            }
        }

        val publishPluginsToTestRepository = tasks.create("publishPluginsToTestRepository").apply {
            dependsOn("publishPluginMavenPublicationToTestRepository")
        }

        val processTestResources = tasks.getByName("processTestResources") as ProcessResources
        val writeTestProperties = tasks.create("writeTestProperties", WriteProperties::class.java).apply {
            outputFile = processTestResources.destinationDir.resolve("test.properties")
            property("version", version)
        }
        processTestResources.dependsOn(writeTestProperties)

        tasks.getByName("test").apply {
            dependsOn(publishPluginsToTestRepository)
        }

        afterEvaluate {
            kotlinDslPlugins.all {
                publishPluginsToTestRepository
                    .dependsOn("publish${it.name.capitalize()}PluginMarkerMavenPublicationToTestRepository")
            }
        }
    }
}


private
val Project.base
    get() = convention.getPlugin(BasePluginConvention::class.java)


private
fun Project.publishing(action: PublishingExtension.() -> Unit) =
    extensions.configure(PublishingExtension::class.java, action)


private
fun Project.gradlePlugin(action: GradlePluginDevelopmentExtension.() -> Unit) =
    extensions.configure(GradlePluginDevelopmentExtension::class.java, action)


private
fun Project.pluginBundle(action: PluginBundleExtension.() -> Unit) =
    extensions.configure(PluginBundleExtension::class.java, action)
