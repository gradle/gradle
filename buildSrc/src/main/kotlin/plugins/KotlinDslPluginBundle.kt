package plugins

import com.gradle.publish.PluginBundleExtension

import org.gradle.api.DomainObjectCollection
import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.WriteProperties

import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.task
import org.gradle.kotlin.dsl.the

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
        tasks["test"].dependsOn(rootProject.tasks["customInstallation"])

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

            kotlinDslPlugins.all {

                val plugin = this

                gradlePlugin {
                    plugins {
                        create(plugin.name) {
                            id = plugin.id
                            implementationClass = plugin.implementationClass
                        }
                    }
                }

                pluginBundle {
                    plugins {
                        create(plugin.name) {
                            id = plugin.id
                            displayName = plugin.displayName
                            description = plugin.displayName
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
                maven {
                    name = "test"
                    url = uri("$buildDir/repository")
                }
            }
        }

        val publishPluginsToTestRepository = tasks.create("publishPluginsToTestRepository") {
            dependsOn("publishPluginMavenPublicationToTestRepository")
        }

        tasks.getByName("test") {
            dependsOn(publishPluginsToTestRepository)
        }

        val writeFuturePluginVersions = createWriteFuturePluginVersionsTask()

        afterEvaluate {

            kotlinDslPlugins.all {

                val plugin = this

                publishPluginsToTestRepository
                    .dependsOn("publish${plugin.name.capitalize()}PluginMarkerMavenPublicationToTestRepository")

                writeFuturePluginVersions
                    .property(plugin.id, version)
            }
        }
    }

    private
    fun Project.createWriteFuturePluginVersionsTask(): WriteProperties {
        val processTestResources = tasks["processTestResources"] as ProcessResources
        return task<WriteProperties>("writeFuturePluginVersions") {
            outputFile = processTestResources.futurePluginVersionsFile
            processTestResources.dependsOn(this)
        }
    }
}


val ProcessResources.futurePluginVersionsFile
    get() = destinationDir.resolve("future-plugin-versions.properties")


private
val Project.base
    get() = the<BasePluginConvention>()


private
fun Project.publishing(action: PublishingExtension.() -> Unit) =
    configure(action)


private
fun Project.gradlePlugin(action: GradlePluginDevelopmentExtension.() -> Unit) =
    configure(action)


private
fun Project.pluginBundle(action: PluginBundleExtension.() -> Unit) =
    configure(action)
