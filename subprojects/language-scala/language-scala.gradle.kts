import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    id("gradlebuild.strict-compile")
}

dependencies {
    compile(project(":core"))
    compile(project(":platformJvm"))
    compile(project(":languageJava"))
    compile(project(":languageJvm"))

    // keep in sync with ScalaLanguagePlugin code
    compileOnly("com.typesafe.zinc:zinc:0.3.15")
}

gradlebuildJava {
    // Needs to run in the compiler daemon
    moduleType = ModuleType.WORKER
}

testFixtures {
    from(":core")
    from(":languageJvm", "testFixtures")
    from(":platformBase")
    from(":launcher")
    from(":plugins")
}
