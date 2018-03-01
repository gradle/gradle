plugins {
    `java-gradle-plugin`
}

apply { plugin("org.gradle.kotlin.kotlin-dsl")}

dependencies {
    compile(project(":build"))
    compile(project(":testing"))
    compile(project(":binaryCompatibility"))
    compile(project(":kotlinDsl"))
    compile("org.pegdown:pegdown:1.6.0")
    compile("org.jsoup:jsoup:1.11.2")
    compile("com.gradle:build-scan-plugin:1.12.1")
    compile("com.google.guava:guava-jdk5:14.0.1")
    compile("org.ow2.asm:asm:6.0")
    compile("org.ow2.asm:asm-commons:6.0")
    testCompile("junit:junit:4.12")
    testCompile("com.nhaarman:mockito-kotlin:1.5.0")
}

gradlePlugin {
    (plugins) {
        "classycle" {
            id = "classycle"
            implementationClass = "org.gradle.plugins.classycle.ClassyclePlugin"
        }
        "testFixtures" {
            id = "test-fixtures"
            implementationClass = "org.gradle.plugins.testfixtures.TestFixturesPlugin"
        }
        "strictCompile" {
            id = "strict-compile"
            implementationClass = "org.gradle.plugins.strictcompile.StrictCompilePlugin"
        }
        "jsoup" {
            id = "jsoup"
            implementationClass = "org.gradle.plugins.jsoup.JsoupPlugin"
        }
        "buildTypes" {
            id = "build-types"
            implementationClass = "org.gradle.plugins.buildtypes.BuildTypesPlugin"
        }
        "gradleCompile" {
            id = "gradle-compile"
            implementationClass = "org.gradle.plugins.compile.GradleCompilePlugin"
        }
        "performanceTest" {
            id = "performance-test"
            implementationClass = "org.gradle.plugins.performance.PerformanceTestPlugin"
        }
        "ideConfiguration" {
            id = "ide-configuration"
            implementationClass = "org.gradle.plugins.ideconfiguration.IdeConfigurationPlugin"
        }
        "configureTaskPropertyValidation" {
            id = "configure-task-properties-validation"
            implementationClass = "org.gradle.plugins.codequality.ConfigureTaskPropertyValidationPlugin"
        }
        "buildscanConfiguration" {
            id = "buildscan-configuration"
            implementationClass = "org.gradle.plugins.buildscan.BuildScanConfigurationPlugin"
        }
        "ciReporting" {
            id = "ci-reporting"
            implementationClass = "org.gradle.plugins.reporting.CiReportingPlugin"
        }
    }
}
