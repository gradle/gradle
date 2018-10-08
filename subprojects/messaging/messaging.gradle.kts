import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    id("gradlebuild.classycle")
}

dependencies {
    api(project(":baseServices"))
    api(library("slf4j_api"))

    implementation(library("kryo"))
}

gradlebuildJava {
    moduleType = ModuleType.ENTRY_POINT
}

testFixtures {
    from(":core")
}
