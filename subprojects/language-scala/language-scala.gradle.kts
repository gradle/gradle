import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    gradlebuild.`strict-compile`
}

dependencies {
    compile(project(":core"))
    compile(project(":platformJvm"))
    compile(project(":languageJava"))
    compile(project(":languageJvm"))

    // keep in sync with ScalaLanguagePlugin code
    compileOnly("org.scala-sbt:zinc_2.12:1.2.5")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
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
