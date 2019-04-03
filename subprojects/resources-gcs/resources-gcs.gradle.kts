import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    id("gradlebuild.strict-compile")
    id("gradlebuild.classycle")
}

dependencies {
    compile(project(":resources"))
    compile(project(":resourcesHttp"))
    compile(project(":core"))
    compile(library("guava"))
    compile(library("jackson_core"))
    compile(library("jackson_annotations"))
    compile(library("jackson_databind"))
    compile(library("gcs"))
    compile(library("commons_httpclient"))
    compile(library("joda"))
    compile(library("commons_lang"))
    testCompile(library("groovy"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":dependencyManagement")
    from(":ivy")
    from(":maven")
}
