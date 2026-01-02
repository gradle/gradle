plugins {
    id("gradlebuild.distribution.implementation-kotlin")
    id("gradlebuild.kotlin-dsl-sam-with-receiver")
}

description = "Implementations and helpers for running under Isolated Projects mode"

dependencies {
    api("org.apache.groovy:groovy:4.0.29")
    api(libs.kotlinStdlib)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.serviceLookup)
    api(projects.serviceProvider)

    implementation(projects.buildOperations)
    implementation(projects.configurationCache)
    implementation(projects.configurationCacheBase)
    implementation(projects.configurationProblemsBase)
    implementation(projects.coreKotlinExtensions)
    implementation(projects.fileCollections)
    implementation(projects.fileOperations)
    implementation(projects.logging)
    implementation(projects.loggingApi)
    implementation(projects.messaging)
    implementation(projects.modelCore)
    implementation(projects.stdlibKotlinExtensions)

    runtimeOnly(projects.kotlinDsl)
}

jvmCompile {
    compilations {
        named("main") {
            targetJvmVersion = 17
        }
    }
}

//packageCycles {
//    excludePatterns.add("org/gradle/internal/cc/**")
//}
