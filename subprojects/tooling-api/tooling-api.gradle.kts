import accessors.*
import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.gradlebuild.packaging.ShadedJar
import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import org.gradle.gradlebuild.unittestandcompile.ModuleType
import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.gradlebuild.packaging.shading.ShadeClassesTransform
import org.gradle.gradlebuild.packaging.shading.FindRelocatedClasses
import org.gradle.gradlebuild.packaging.shading.FindClassTrees
import org.gradle.gradlebuild.packaging.shading.FindEntryPoints
import org.gradle.gradlebuild.packaging.shading.CreateShadedJar

val testPublishRuntime by configurations.creating

val jar: Jar by tasks

val artifactType: Attribute<String> = Attribute.of("artifactType", String::class.java)
val minified: Attribute<Boolean> = Attribute.of("minified", Boolean::class.javaObjectType)

val jarsToShade by configurations.creating
jarsToShade.apply {
    exclude(mapOf("group" to "org.slf4j", "module" to "slf4j-api"))
    jarsToShade.extendsFrom(configurations.runtimeClasspath)
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    compile(project(":core"))
    compile(project(":messaging"))
    compile(project(":wrapper"))
    compile(project(":baseServices"))
    publishCompile(library("slf4j_api")) { version { prefer(libraryVersion("slf4j_api")) } }
    compile(library("jcip"))

    testFixturesCompile(project(":baseServicesGroovy"))
    testFixturesCompile(project(":internalIntegTesting"))

    integTestRuntime(project(":toolingApiBuilders"))
    integTestRuntime(project(":ivy"))

    crossVersionTestRuntime("org.gradle:gradle-kotlin-dsl:${BuildEnvironment.gradleKotlinDslVersion}")
    crossVersionTestRuntime(project(":buildComparison"))
    crossVersionTestRuntime(project(":ivy"))
    crossVersionTestRuntime(project(":maven"))

    registerTransform {
        from.attribute(artifactType, "jar").attribute(minified, true)
        to.attribute(artifactType, "relocatedClassesAndAnalysis")
        artifactTransform(ShadeClassesTransform::class.java) {
            params(
                "org.gradle.internal.impldep",
                setOf("org.gradle.tooling"),
                setOf("org.gradle", "org.slf4j", "sun.misc"),
                setOf("org.gradle.tooling.provider.model")
            )
        }
    }
    registerTransform {
        from.attribute(artifactType, "relocatedClassesAndAnalysis")
        to.attribute(artifactType, "relocatedClasses")
        artifactTransform(FindRelocatedClasses::class.java)
    }
    registerTransform {
        from.attribute(artifactType, "relocatedClassesAndAnalysis")
        to.attribute(artifactType, "entryPoints")
        artifactTransform(FindEntryPoints::class.java)
    }
    registerTransform {
        from.attribute(artifactType, "relocatedClassesAndAnalysis")
        to.attribute(artifactType, "classTrees")
        artifactTransform(FindClassTrees::class.java)
    }
}

gradlebuildJava {
    moduleType = ModuleType.ENTRY_POINT
}

testFixtures {
    from(":core")
    from(":logging")
    from(":dependencyManagement")
    from(":ide")
}

val baseVersion: String by rootProject.extra

val shadedJarWithoutVersion by tasks.creating(ShadedJar::class) {
    val outputDir = file("$buildDir/shaded-jar-without-version")
    sourceFiles = jar.outputs.files +
        files(deferred { configurations.runtimeClasspath - configurations.publishCompile })
    analysisFile = file("$outputDir/analysis.txt")
    classesDir = file("$outputDir/classes")
    jarFile = file("$outputDir/gradle-tooling-api-shaded-$baseVersion.jar")
    keepPackages = setOf("org.gradle.tooling")
    unshadedPackages = setOf("org.gradle", "org.slf4j", "sun.misc")
    ignorePackages = setOf("org.gradle.tooling.provider.model")
    shadowPackage = "org.gradle.internal.impldep"
}

val buildReceipt = tasks.getByPath(":createBuildReceipt")

val toolingApiShadedJar by tasks.creating(Zip::class) {
    destinationDir = file("$buildDir/shaded-jar")
    dependsOn(shadedJarWithoutVersion, buildReceipt)
    from(zipTree(shadedJarWithoutVersion.jarFile))
    baseName = "gradle-tooling-api-shaded"
    from(buildReceipt) {
        into("/org/gradle")
    }
    extension = "jar"
    version = baseVersion
}

apply { from("buildship.gradle") }

val sourceJar: Jar by tasks

sourceJar.run {
    configurations.compile.allDependencies.withType<ProjectDependency>().forEach {
        from(it.dependencyProject.java.sourceSets[SourceSet.MAIN_SOURCE_SET_NAME].groovy.srcDirs)
        from(it.dependencyProject.java.sourceSets[SourceSet.MAIN_SOURCE_SET_NAME].java.srcDirs)
    }
}

eclipse {
    classpath {
        file.whenMerged(Action<Classpath> {
            //**TODO
            entries.removeAll { path.contains("src/test/groovy") }
            entries.removeAll { path.contains("src/integTest/groovy") }
        })
    }
}

artifacts {
    add("publishRuntime", mapOf(
        "file" to toolingApiShadedJar.archivePath,
        "name" to base.archivesBaseName,
        "type" to "jar",
        "builtBy" to toolingApiShadedJar
    ))
}

tasks.create<Upload>("publishLocalArchives") {
    val repoBaseDir = rootProject.file("build/repo")
    configuration = configurations.publishRuntime
    isUploadDescriptor = false
    repositories {
        ivy {
            artifactPattern("$repoBaseDir/${project.group.toString().replace(".", "/")}/${base.archivesBaseName}/[revision]/[artifact]-[revision](-[classifier]).[ext]")
        }
    }

    doFirst {
        if (repoBaseDir.exists()) {
            // Make sure tooling API artifacts do not pile up
            repoBaseDir.deleteRecursively()
        }
    }
}

val integTestTasks: DomainObjectCollection<IntegrationTest> by extra

integTestTasks.all {
    binaryDistributions.binZipRequired = true
    libsRepository.required = true
}

testFilesCleanup.isErrorWhenNotEmpty = false

afterEvaluate {
    dependencies {
        add(jarsToShade.name, project)
    }
}

tasks.create<CreateShadedJar>("combineShaded") {
    val configToShade = jarsToShade
    dependsOn(jar)
    classTreesConfiguration = configToShade.artifactViewForType("classTrees")
    entryPointsConfiguration = configToShade.artifactViewForType("entryPoints")
    relocatedClassesConfiguration = configToShade.artifactViewForType("relocatedClasses")
}


fun Configuration.artifactViewForType(artifactTypeName: String) = incoming.artifactView {
    attributes.attribute(artifactType, artifactTypeName)
}.files
