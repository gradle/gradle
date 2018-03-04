plugins {
    `java-gradle-plugin`
}

apply { plugin("org.gradle.kotlin.kotlin-dsl") }

dependencies {
    implementation(project(":binaryCompatibility"))
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
    implementation(project(":profiling"))
    implementation(project(":testing"))
    implementation("org.owasp:dependency-check-gradle:3.1.0")
}

gradlePlugin {
    (plugins) {
        "dependencyVulnerabilities" {
            id = "gradlebuild.dependency-vulnerabilities"
            implementationClass = "org.gradle.gradlebuild.buildquality.DependencyVulnerabilitiesPlugin"
        }
        "noResolutionAtConfigurationTime" {
            id = "gradlebuild.no-resolution-at-configuration"
            implementationClass = "org.gradle.gradlebuild.buildquality.NoResolutionAtConfigurationTimePlugin"
        }
        "taskPropertyValidation" {
            id = "gradlebuild.task-properties-validation"
            implementationClass = "org.gradle.gradlebuild.buildquality.TaskPropertyValidationPlugin"
        }
        "classycle" {
            id = "gradlebuild.classycle"
            implementationClass = "org.gradle.gradlebuild.buildquality.classycle.ClassyclePlugin"
        }
        "ciReporting" {
            id = "gradlebuild.ci-reporting"
            implementationClass = "org.gradle.gradlebuild.buildquality.CiReportingPlugin"
        }


    }
}


