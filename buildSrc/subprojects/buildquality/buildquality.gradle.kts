plugins {
    `java-gradle-plugin`
}

apply { plugin("org.gradle.kotlin.kotlin-dsl") }

dependencies {
    compile(project(":configuration"))
    compile(project(":kotlinDsl"))
    compile("org.owasp:dependency-check-gradle:3.1.0")
}

gradlePlugin {
    (plugins) {
        "dependencyVulnerabilities" {
            id = "dependency-vulnerabilities"
            implementationClass = "org.gradle.gradlebuild.buildquality.DependencyVulnerabilitiesPlugin"
        }
        "failDependencyResolutionAtConfiguration" {
            id = "fail-dependency-resolution-during-configuration"
            implementationClass = "org.gradle.gradlebuild.buildquality.FailDependencyResolutionAtConfiguration"
        }
        "configureTaskPropertyValidation" {
            id = "configure-task-properties-validation"
            implementationClass = "org.gradle.gradlebuild.buildquality.ConfigureTaskPropertyValidationPlugin"
        }


    }
}


