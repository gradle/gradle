kotlinDslPlugin {

    description = "Provides plugins to create and configure integration, cross-version and distribution tests"

    gradlePlugins {
        gradlePlugin("androidStudioProvisioning") {
            id = "gradlebuild.android-studio-provisioning"
            implementationClass = "gradlebuild.integrationtests.ide.AndroidStudioProvisioningPlugin"
        }

        gradlePlugin("androidHomeWarmup") {
            id = "gradlebuild.android-home-warmup"
            implementationClass = "gradlebuild.integrationtests.androidhomewarmup.AndroidHomeWarmupPlugin"
        }
    }

    dependencies {
        implementation("gradlebuild:basics")
        implementation("gradlebuild:module-identity")

        implementation(project(":cleanup"))
        implementation(project(":dependency-modules"))
        implementation(project(":jvm"))

        testImplementation(catalog("testLibs.junit"))
    }
}
