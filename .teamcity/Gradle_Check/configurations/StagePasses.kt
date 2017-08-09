package configurations

import jetbrains.buildServer.configs.kotlin.v10.BuildStep
import jetbrains.buildServer.configs.kotlin.v10.BuildType
import jetbrains.buildServer.configs.kotlin.v10.FailureAction
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v10.triggers.ScheduleTrigger
import jetbrains.buildServer.configs.kotlin.v10.triggers.VcsTrigger
import jetbrains.buildServer.configs.kotlin.v10.triggers.schedule
import jetbrains.buildServer.configs.kotlin.v10.triggers.vcs
import model.CIBuildModel
import model.Stage
import model.TestType
import model.Trigger

class StagePasses(model: CIBuildModel, stage: Stage, prevStage: Stage?) : BuildType({
    uuid = "${model.projectPrefix}Stage_${stage.name.replace(" ","")}_Trigger"
    extId = uuid
    name = stage.name + " (Trigger)"

    applyDefaultSettings(this)
    artifactRules = "build/build-receipt.properties"

    val triggerExcludes = """
        -:design-docs
        -:subprojects/docs/src/docs/release
    """.trimIndent()
    val masterReleaseFiler = model.masterAndReleaseBranches.joinToString(prefix = "+:", separator = "\n+:")

    if (stage.trigger == Trigger.eachCommit) {
        triggers.vcs {
            quietPeriodMode = VcsTrigger.QuietPeriodMode.USE_CUSTOM
            quietPeriod = 90
            triggerRules = triggerExcludes
            branchFilter = masterReleaseFiler
        }
    } else if (stage.trigger != Trigger.never) {
        triggers.schedule {
            if (stage.trigger == Trigger.weekly) {
                schedulingPolicy = weekly {
                    dayOfWeek = ScheduleTrigger.DAY.Saturday
                    hour = 1
                }
            } else {
                schedulingPolicy = daily {
                    hour = 0
                    minute = 30
                }
            }
            triggerBuild = always()
            withPendingChangesOnly = true
            param("revisionRule", "lastFinished")
            param("branchFilter", masterReleaseFiler)
        }

    }

    steps {
        gradle {
            name = "GRADLE_RUNNER"
            tasks = "createBuildReceipt"
            gradleParams = "-PtimestampedVersion -Djava7.home=%linux.jdk.for.gradle.compile%"
            useGradleWrapper = true
        }
        script {
            name = "CHECK_CLEAN_M2"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = m2CleanScriptLinux
        }
        if (model.tagBuilds) {
            gradle {
                name = "TAG_BUILD"
                executionMode = BuildStep.ExecutionMode.ALWAYS
                tasks = "tagBuild"
                buildFile = "gradle/buildTagging.gradle"
                gradleParams = "-PteamCityUsername=%teamcity.username.restbot% -PteamCityPassword=%teamcity.password.restbot% -PteamCityBuildId=%teamcity.build.id% -PgithubToken=%github.ci.oauth.token%"
                useGradleWrapper = true
            }
        }
    }

    dependencies {
        if (prevStage != null) {
            dependency("${model.projectPrefix}Stage_${prevStage.name.replace(" ","")}_Trigger") {
                snapshot {
                    onDependencyFailure = FailureAction.ADD_PROBLEM
                }
            }
        }

        stage.specificBuilds.forEach {
            dependency(it.create(model)) {
                snapshot {}
            }
        }

        stage.performanceTests.forEach { performanceTest ->
            dependency(performanceTest.asId(model)) {
                snapshot {}
            }
        }

        stage.functionalTests.forEach { testCoverage ->
            val isSplitIntoBuckets = testCoverage.testType != TestType.soak
            if (isSplitIntoBuckets) {
                model.subProjects.forEach { subProject ->
                    if (subProject.unitTests && testCoverage.testType.unitTests) {
                        dependency(testCoverage.asConfigurationId(model, subProject.name)) { snapshot {} }
                    } else if (subProject.functionalTests && testCoverage.testType.functionalTests) {
                        dependency(testCoverage.asConfigurationId(model, subProject.name)) { snapshot {} }
                    } else if (subProject.crossVersionTests && testCoverage.testType.crossVersionTests) {
                        dependency(testCoverage.asConfigurationId(model, subProject.name)) { snapshot {} }
                    }
                }
            } else {
                dependency(testCoverage.asConfigurationId(model)) {
                    snapshot {}
                }
            }
        }
    }
})
