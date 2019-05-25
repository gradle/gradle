import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    
    implementation(library("fastutil"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("kryo"))
}

gradlebuildJava {
    moduleType = ModuleType.WORKER
}

testFixtures {
    from(":core")
}
