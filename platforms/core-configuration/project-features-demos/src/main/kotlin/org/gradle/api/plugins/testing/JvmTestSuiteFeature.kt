package org.gradle.api.plugins.testing

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.PluginManager
import org.gradle.api.plugins.java.JavaProjectType
import org.gradle.api.plugins.jvm.JvmComponentDependencies
import org.gradle.features.annotations.BindsProjectFeature
import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition
import org.gradle.features.binding.ProjectFeatureApplicationContext
import org.gradle.features.binding.ProjectFeatureBinding
import org.gradle.features.binding.ProjectFeatureBindingBuilder
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskContainer
import org.gradle.features.binding.BuildModelRegistrar
import org.gradle.features.binding.ProjectFeatureApplyAction
import org.gradle.features.dsl.bindProjectFeature
import org.gradle.testing.base.TestingExtension
import javax.inject.Inject

/**
 * Demo for mixed usage of "old" plugins
 */
@BindsProjectFeature(JvmTestSuiteFeature::class)
abstract class JvmTestSuiteFeature : Plugin<Project>, ProjectFeatureBinding {
    override fun apply(target: Project) {}
    override fun bind(builder: ProjectFeatureBindingBuilder) {
        builder.bindProjectFeature("testing", JvmTestSuiteFeatureAction::class)
            .withUnsafeDefinition()
            .withUnsafeApplyAction()
    }

    abstract class JvmTestSuiteFeatureAction :
        ProjectFeatureApplyAction<DclTestingExtension, BuildModel.None, JavaProjectType> {
        @get:Inject
        abstract val pluginManager: PluginManager

        @get:Inject
        abstract val tasks: TaskContainer

        @get:Inject
        abstract val project: Project

        @get:Inject
        abstract val buildModelRegistrar: BuildModelRegistrar

        override fun apply(
            context: ProjectFeatureApplicationContext,
            definition: DclTestingExtension,
            buildModel: BuildModel.None,
            parentDefinition: JavaProjectType,
        ) {
            pluginManager.apply("jvm-test-suite")

            val testing = project.extensions.getByName("testing") as TestingExtension

            definition.getSuites().all { dclJvmSuite ->
                val buildModel = buildModelRegistrar.registerBuildModel(dclJvmSuite, DefaultJvmDclTestSuiteBuildModel::class.java)
                buildModel as DefaultJvmDclTestSuiteBuildModel

                val action: Action<JvmTestSuite> = Action { jvmTestSuite ->
                    buildModel.testSuite = jvmTestSuite

                    jvmTestSuite.dependencies.implementation.bundle(dclJvmSuite.dependencies.implementation.dependencies)
                    jvmTestSuite.dependencies.compileOnly.bundle(dclJvmSuite.dependencies.compileOnly.dependencies)
                    jvmTestSuite.dependencies.runtimeOnly.bundle(dclJvmSuite.dependencies.runtimeOnly.dependencies)
                    jvmTestSuite.dependencies.annotationProcessor.bundle(dclJvmSuite.dependencies.annotationProcessor.dependencies)

                    dclJvmSuite.getTargets().all { dclTestSuiteTarget ->
                        if (dclTestSuiteTarget.name == jvmTestSuite.name) {
                            jvmTestSuite.targets.named(dclTestSuiteTarget.name)
                        } else {
                            jvmTestSuite.targets.register(dclTestSuiteTarget.name)
                        }
                    }
                }
                if (dclJvmSuite.name == "test") {
                    project.afterEvaluate {
                        // the Java plugin always uses `register`
                        testing.suites.named(dclJvmSuite.name, JvmTestSuite::class.java, action)
                    }
                } else {
                    testing.suites.register(dclJvmSuite.name, JvmTestSuite::class.java, action)
                }
            }
        }
    }
}

// Can't reuse TestingExtension from core-api because of DomainObjectCollection<? extends TestSuiteTarget> getTargets();
// OUT/? extends is not (yet?) supported in DCL
interface DclTestingExtension : Definition<BuildModel.None> {
    @Nested
    fun getSuites(): NamedDomainObjectContainer<JvmDclTestSuite>
}

// Can't extend TestSuite from core-api because of DomainObjectCollection<? extends TestSuiteTarget> getTargets();
// OUT/? extends is not (yet?) supported in DCL
interface JvmDclTestSuite : Definition<JvmDclTestSuiteBuildModel>, Named {
    @Nested
    fun getTargets(): NamedDomainObjectContainer<JvmDclTestSuiteTarget>

    @get:Nested
    val dependencies: JvmComponentDependencies
}

interface JvmDclTestSuiteBuildModel : BuildModel {
    val testSuite: JvmTestSuite
}

internal abstract class DefaultJvmDclTestSuiteBuildModel : JvmDclTestSuiteBuildModel {
    override lateinit var testSuite: JvmTestSuite
}

// Not extending TestSuiteTarget due to https://github.com/gradle/gradle/issues/36410
interface JvmDclTestSuiteTarget : Named {
    // https://github.com/gradle/gradle/issues/36410
    // override fun getBinaryResultsDirectory(): DirectoryProperty
}
