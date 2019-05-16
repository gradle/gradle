import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    api(project(":cli"))

    compile(project(":coreApi"))
    compile(project(":launcherBootstrap"))
    compile(project(":jvmServices"))
    compile(project(":core"))
    compile(project(":buildOption"))
    compile(project(":toolingApi"))
    compile(project(":native"))
    compile(project(":logging"))
    compile(project(":messaging"))
    compile(project(":baseServices"))
    compile(project(":files"))

    compile(library("guava"))
    compile(library("asm"))
    compile(library("commons_io"))
    compile(library("commons_lang"))
    compile(library("ant"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}
