plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides plugins to create and configure integration, cross-version and distribution tests"

gradlePlugin {
    plugins {
        register("ideProvisioning") {
            id = "gradlebuild.ide-provisioning"
            implementationClass = "gradlebuild.integrationtests.ide.IdeProvisioningPlugin"
        }
    }

    plugins {
        register("androidHomeWarmup") {
            id = "gradlebuild.android-home-warmup"
            implementationClass = "gradlebuild.integrationtests.androidhomewarmup.AndroidHomeWarmupPlugin"
        }
    }
}

dependencies {
    implementation("gradlebuild:basics")
    implementation("gradlebuild:module-identity")

    implementation(projects.cleanup)
    implementation(projects.dependencyModules)
    implementation(projects.jvm)

    implementation(testLibs.intellijPlatform)
}
