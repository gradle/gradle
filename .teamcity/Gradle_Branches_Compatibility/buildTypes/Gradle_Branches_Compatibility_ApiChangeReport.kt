package Gradle_Branches_Compatibility.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle

object Gradle_Branches_Compatibility_ApiChangeReport : BuildType({
    uuid = "62569f76-861b-4ad0-905c-9747a6724925"
    extId = "Gradle_Branches_Compatibility_ApiChangeReport"
    name = "API Change Report"
    description = "Generates a JDiff API report for the upcoming release"

    artifactRules = """incoming-build-receipt/build-receipt.properties => incoming-build-receipt
promote-projects/gradle/build/reports/jdiff => jdiff"""
    maxRunningBuilds = 3

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }

    vcs {
        root("Gradle_Promotion__master_")

        checkoutMode = CheckoutMode.ON_AGENT
    }

    steps {
        gradle {
            name = "GRADLE_RUNNER"
            tasks = "clean jdiff"
            gradleParams = "-P OLD_VERSION=3.1"
            useGradleWrapper = true
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
        }
    }

    failureConditions {
        executionTimeoutMin = 120
    }

    requirements {
        equals("teamcity.agent.name", "agent1")
    }
})
