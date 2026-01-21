plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    api(projects.baseServices)
    api(projects.classloaders)
    api(projects.coreApi)
    api(projects.hashing)
    api(projects.internalInstrumentationApi)
    api(projects.javaCompilerWorker)
    api(projects.languageJvm)
    api(projects.persistentCache)
    api(projects.platformBase)
    api(projects.scopedPersistentCache)
    api(projects.stdlibJavaExtensions)
    api("com.google.guava:guava")
    api("javax.inject:javax.inject")
    api("org.jspecify:jspecify")
    api("org.scala-lang:scala-library")
    api("org.scala-sbt:compiler-interface")
    api("org.scala-sbt:util-interface")
    api("org.scala-sbt:zinc-classpath_2.13")
    api("org.scala-sbt:zinc-compile-core_2.13")
    api("org.scala-sbt:zinc-core_2.13")

    implementation(projects.loggingApi)
    implementation(projects.time)
    implementation("org.scala-sbt:io_2.13")
    implementation("org.scala-sbt:util-logging_2.13")
    implementation("org.scala-sbt:util-relation_2.13")
    implementation("org.scala-sbt:zinc-persist_2.13")
    implementation("org.scala-sbt:zinc_2.13")
}
