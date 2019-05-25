import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":cli"))
    implementation(project(":messaging"))
    implementation(project(":buildOption"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":files"))
    implementation(project(":persistentCache"))
    implementation(project(":coreApi"))
    implementation(project(":core"))
    implementation(project(":launcherBootstrap"))
    implementation(project(":jvmServices"))
    implementation(project(":toolingApi"))

    implementation(library("groovy")) // for 'ReleaseInfo.getVersion()'
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("commons_io"))
    implementation(library("commons_lang"))
    implementation(library("asm"))
    implementation(library("ant"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}
