buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.apache.commons:commons-math3:3.6.1")
    }

}

configurations.create("spi")

// tag::dependencies[]
// tag::project-dependencies[]
dependencies {
    implementation(project(":shared"))
// end::project-dependencies[]
// end::dependencies[]
    implementation(module("org.apache.commons:commons-lang3:3.7") {
        dependency("commons-io:commons-io:2.6")
    })
// tag::dependencies[]
// tag::project-dependencies[]
}
// end::dependencies[]
// end::project-dependencies[]

// Just a smoke test that using this option does not lead to any exception
tasks.compileJava { options.compilerArgs = listOf("-Xlint:unchecked") }

task<Jar>("spiJar") {
    archiveAppendix.set("spi")
    from(sourceSets.main.get().output)
    include("org/gradle/api/")
}

artifacts {
    add("spi", tasks["spiJar"])
}

// tag::dists[]
val dist = task<Zip>("dist") {
    val spiJar = tasks.getByName<Jar>("spiJar")
    dependsOn(spiJar)
    from("src/dist")
    into("libs") {
        from(spiJar.archiveFile)
        from(configurations.runtimeClasspath)
    }
}

artifacts {
    add("archives", dist)
}
// end::dists[]

// We want to test if commons-math was properly added to the build script classpath
val lhs = org.apache.commons.math3.fraction.Fraction(1, 3);
val bsc = org.gradle.buildsrc.BuildSrcClass()

task("checkProjectDependency") {
    val sharedJarTask = project(":shared").tasks.getByName<Jar>("jar")
    dependsOn(sharedJarTask)
    doLast {
        val cachedSharedJarDir = File(gradle.gradleUserHomeDir, "cache/multiproject/shared/jars")
        copy {
            from(sharedJarTask.archiveFile)
            into(cachedSharedJarDir)
        }
        val sharedJar = configurations.compileClasspath.get().first { file: File -> file.name.startsWith("shared") }
        require(sharedJar.absolutePath == sharedJarTask.archiveFile.get().getAsFile().absolutePath)
    }
}
