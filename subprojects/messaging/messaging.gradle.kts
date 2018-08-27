
plugins {
    `java-library`
    id("gradlebuild.classycle")
}

dependencies {
    api(project(":baseServices"))
    api(library("slf4j_api"))

    implementation(library("kryo"))
}

testFixtures {
    from(":core")
}
