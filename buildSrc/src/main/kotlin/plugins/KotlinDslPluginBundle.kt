package plugins

import org.gradle.api.DomainObjectCollection
import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.WriteProperties

import org.gradle.language.jvm.tasks.ProcessResources

import org.gradle.plugin.devel.GradlePluginDevelopmentExtension

import com.gradle.publish.PluginBundleExtension


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

        kotlinDslPlugins = container(KotlinDslPlugin::class.java)
        extensions.add("kotlinDslPlugins", kotlinDslPlugins)

        plugins.apply(KotlinDslModule::class.java)
        plugins.apply("maven-publish")
        plugins.apply("java-gradle-plugin")
        plugins.apply("com.gradle.plugin-publish")

        testDependsOnCustomInstallation()

        configureGradlePluginDevelopmentPlugins()

        workAroundTestKitWithPluginClassPathIssues()
    }

    private
    fun Project.testDependsOnCustomInstallation() =
        tasks.getByName("test").dependsOn(rootProject.tasks.getByName("customInstallation"))

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
    // See https://github.com/gradle/kotlin-dsl/issues/492
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

        tasks.getByName("test").apply {
            dependsOn(publishPluginsToTestRepository)
        }

        val writeFuturePluginVersions = createWriteFuturePluginVersionsTask()

        afterEvaluate {

            kotlinDslPlugins.all { plugin ->

                publishPluginsToTestRepository
                    .dependsOn("publish${plugin.name.capitalize()}PluginMarkerMavenPublicationToTestRepository")

                writeFuturePluginVersions
                    .property(plugin.id, version)
            }
        }
    }

    private
    fun Project.createWriteFuturePluginVersionsTask(): WriteProperties {
        val processTestResources = tasks.getByName("processTestResources") as ProcessResources
        return tasks.create("writeFuturePluginVersions", WriteProperties::class.java).apply {
            outputFile = processTestResources.futurePluginVersionsFile
            processTestResources.dependsOn(this)
        }
    }
}


val ProcessResources.futurePluginVersionsFile
    get() = destinationDir.resolve("future-plugin-versions.properties")


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
