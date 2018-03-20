import org.gradle.gradlebuild.packaging.shading.CreateShadedJar
import org.gradle.gradlebuild.packaging.shading.FindClassTrees
import org.gradle.gradlebuild.packaging.shading.FindEntryPoints
import org.gradle.gradlebuild.packaging.shading.FindRelocatedClasses
import org.gradle.gradlebuild.packaging.shading.ShadeClassesTransform
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
}

gradlebuildJava {
    moduleType = ModuleType.ENTRY_POINT
}

val artifactType: Attribute<String> = Attribute.of("artifactType", String::class.java)
val minified: Attribute<Boolean> = Attribute.of("minified", Boolean::class.javaObjectType)


val relocatedClasses by configurations.creating
val classTrees by configurations.creating
val entryPoints by configurations.creating

listOf(relocatedClasses, classTrees, entryPoints).forEach {
    it.apply {
        extendsFrom(configurations.runtimeClasspath)
    }
}

relocatedClasses.attributes.attribute(artifactType, "relocatedClasses")
classTrees.attributes.attribute(artifactType, "classTrees")
entryPoints.attributes.attribute(artifactType, "entryPoints")

listOf(relocatedClasses, classTrees, entryPoints).forEach {
    it.apply {
        exclude(mapOf("group" to "org.slf4j", "module" to "slf4j-api"))
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
    }
}

dependencies {
    implementation(project(":toolingApi"))

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

tasks.create("testResolution") {
    inputs.files(relocatedClasses)
    inputs.files(entryPoints)
    inputs.files(classTrees)
    doLast {
        //        listOf(relocatedClasses, entryPoints, classTrees).forEach { configuration ->
        entryPoints.files.forEach {
            println(it)
        }
//        }
    }
}

tasks.create<CreateShadedJar>("combineShaded") {
    classTreesConfiguration = classTrees
    entryPointsConfiguration = entryPoints
    relocatedClassesConfiguration = relocatedClasses
}
