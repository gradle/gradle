import gradlebuild.basics.buildId
import gradlebuild.packaging.transforms.CopyPublicApiClassesTransform

plugins {
    id("gradlebuild.distribution.packaging")
    id("gradlebuild.verify-build-environment")
    id("gradlebuild.install")
}

description = "The collector project for the entirety of the Gradle distribution"

enum class Filtering {
    PUBLIC_API, ALL
}

val filteredAttribute: Attribute<Filtering> = Attribute.of("org.gradle.apijar.filtered", Filtering::class.java)

dependencies {
    coreRuntimeOnly(platform(project(":core-platform")))

    agentsRuntimeOnly(project(":instrumentation-agent"))

    pluginsRuntimeOnly(platform(project(":distributions-publishing")))
    pluginsRuntimeOnly(platform(project(":distributions-jvm")))
    pluginsRuntimeOnly(platform(project(":distributions-native")))

    pluginsRuntimeOnly(project(":plugin-development"))
    pluginsRuntimeOnly(project(":build-configuration"))
    pluginsRuntimeOnly(project(":build-init"))
    pluginsRuntimeOnly(project(":build-profile"))
    pluginsRuntimeOnly(project(":antlr"))
    pluginsRuntimeOnly(project(":enterprise"))
    pluginsRuntimeOnly(project(":unit-test-fixtures"))

    artifactTypes.getByName("jar") {
        attributes.attribute(filteredAttribute, Filtering.ALL)
    }

    // Filters out classes that are not exposed by our API.
    // TODO Use actual filtering not copying
    registerTransform(CopyPublicApiClassesTransform::class.java) {
        from.attribute(filteredAttribute, Filtering.ALL)
            .attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, LibraryElements.JAR))
        to.attribute(filteredAttribute, Filtering.PUBLIC_API)
    }
}

// This is required for the separate promotion build and should be adjusted there in the future
tasks.register<Copy>("copyDistributionsToRootBuild") {
    dependsOn("buildDists")
    from(layout.buildDirectory.dir("distributions"))
    into(rootProject.layout.buildDirectory.dir("distributions"))
}

tasks.register<Jar>("jarPublicApi") {
    from(configurations.runtimeClasspath.map { classpath ->
        classpath.incoming.artifactView {
            attributes {
                attribute(filteredAttribute, Filtering.PUBLIC_API)
            }
            componentFilter {
                it is ProjectComponentIdentifier &&
                    // FIXME Why is this dependency present here? Can we exclude it better?
                    buildTreePath != ":build-logic:kotlin-dsl-shared-runtime"
            }
        }.files
    })
    destinationDirectory = layout.buildDirectory.dir("public-api")
    // FIXME This is required because package-info.class files are duplicated
    duplicatesStrategy = DuplicatesStrategy.WARN
}
