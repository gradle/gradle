plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides plugins to create and configure integration, cross-version and distribution tests"

gradlePlugin {
    plugins {
        register("androidStudioProvisioning") {
            id = "gradlebuild.android-studio-provisioning"
            implementationClass = "gradlebuild.integrationtests.ide.AndroidStudioProvisioningPlugin"
        }
    }
}

dependencies {
    implementation("gradlebuild:basics")
    implementation("gradlebuild:module-identity")

    implementation(projects.cleanup)
    implementation(projects.dependencyModules)

    testImplementation("junit:junit")
}
