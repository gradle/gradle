plugins {
    java
}

repositories {
    mavenCentral()
}

// tag::locking-file-name[]
val scalaVersion = "2.12"
dependencyLocking {
    lockFile.set(file("$projectDir/locking/gradle-${scalaVersion}.lockfile"))
}
// end::locking-file-name[]


// tag::locking-explicit[]
configurations {
    compileClasspath {
        resolutionStrategy.activateDependencyLocking()
    }
    runtimeClasspath {
        resolutionStrategy.activateDependencyLocking()
    }
    annotationProcessor {
        resolutionStrategy.activateDependencyLocking()
    }
}

dependencies {
    implementation("org.springframework:spring-beans:[5.0,6.0)")
}
// end::locking-explicit[]
