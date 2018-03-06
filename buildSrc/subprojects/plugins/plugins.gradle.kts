plugins {
    `java-gradle-plugin`
}

apply { plugin("org.gradle.kotlin.kotlin-dsl") }

dependencies {
    implementation(project(":binaryCompatibility"))
    implementation(project(":build"))
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
    implementation(project(":testing"))
    implementation(project(":versioning"))
    implementation("org.pegdown:pegdown:1.6.0")
    implementation("org.jsoup:jsoup:1.11.2")
    implementation("com.google.guava:guava-jdk5:14.0.1")
    implementation("org.ow2.asm:asm:6.0")
    implementation("org.ow2.asm:asm-commons:6.0")
    implementation("com.google.code.gson:gson:2.7")
    testImplementation("junit:junit:4.12")
    testImplementation("com.nhaarman:mockito-kotlin:1.5.0")
}

gradlePlugin {
    (plugins) {
        "buildTypes" {
            id = "gradlebuild.build-types"
            implementationClass = "org.gradle.plugins.buildtypes.BuildTypesPlugin"
        }
        "gradleCompile" {
            id = "gradlebuild.gradle-compile"
            implementationClass = "org.gradle.plugins.compile.GradleCompilePlugin"
        }
        "jsoup" {
            id = "gradlebuild.jsoup"
            implementationClass = "org.gradle.plugins.jsoup.JsoupPlugin"
        }
        "performanceTest" {
            id = "gradlebuild.performance-test"
            implementationClass = "org.gradle.plugins.performance.PerformanceTestPlugin"
        }
        "publishPublicLibraries" {
            id = "gradlebuild.publish-public-libraries"
            implementationClass = "org.gradle.plugins.publish.PublishPublicLibrariesPlugin"
        }
       "resumeBuild" {
            id = "gradlebuild.resume-build"
            implementationClass = "org.gradle.gradlebuild.tools.ResumeBuildPlugin"
        }
        "strictCompile" {
            id = "gradlebuild.strict-compile"
            implementationClass = "org.gradle.plugins.strictcompile.StrictCompilePlugin"
        }
        "testFixtures" {
            id = "gradlebuild.test-fixtures"
            implementationClass = "org.gradle.plugins.testfixtures.TestFixturesPlugin"
        }
        "unitTestAndCompile" {
            id = "gradlebuild.unittest-and-compile"
            implementationClass = "org.gradle.gradlebuild.unittestandcompile.UnitTestAndCompilePlugin"
        }
        "integrationTests" {
            id = "gradlebuild.integration-tests"
            implementationClass = "org.gradle.plugins.integrationtests.IntegrationTestsPlugin"
        }
        "crossVersionTests" {
            id = "gradlebuild.cross-version-tests"
            implementationClass = "org.gradle.plugins.integrationtests.CrossVersionTestsPlugin"
        }
    }
}
