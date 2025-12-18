/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.publish-public-libraries")
    id("gradlebuild.jmh")
}

description = "Gradle optimized persistent collection implementations suitable for use in all environments, including workers."

gradleModule {
    targetRuntimes {
        usedInWorkers = true
    }
}

dependencies {
    api(libs.jspecify)

    implementation(projects.stdlibJavaExtensions)

    // For benchmarking against capsule, clojure, scala collections and more.
    // Uncomment the libraries you want to benchmark against then update
    // the verification metadata with:
    // ./gradlew help --write-verification-metadata pgp,sha256 --export-keys
//    jmhImplementation("io.usethesource:capsule:0.7.1")
//    jmhImplementation("com.github.krukow:clj-ds:0.0.4")
//    jmhImplementation("org.scala-lang:scala3-library_3:3.7.4")
//    jmhImplementation("org.pcollections:pcollections:5.0.0")

    jmhImplementation(libs.guava)
    jmhImplementation(libs.fastutil)
}

errorprone {
    nullawayEnabled = true
}

jmh {
    resultFormat = "json"
    includes.addAll(
//        "PersistentArrayBenchmark",
//        "PersistentArrayBenchmark.append",
//        "PersistentArrayBenchmark.iteration",
//        "PersistentArrayBenchmark.randomAccess",
//        "PersistentArrayBenchmark.indexBasedIteration",
//        "PersistentArrayBenchmark.constructionOneByOne",
//        "PersistentSetBenchmark",
//        "PersistentSetBenchmark.iteration",
//        "PersistentSetBenchmark.randomLookup",
//        "PersistentSetBenchmark.remove",
//        "PersistentSetBenchmark.removeAbsent",
//        "PersistentSetBenchmark.removePresent",
//        "PersistentSetBenchmark.removeMany",
//        "PersistentSetBenchmark.randomInsert",
//        "PersistentSetBenchmark.constructionOneByOne",
//        "PersistentMapBenchmark",
//        "PersistentMapBenchmark.modify",
//        "PersistentMapBenchmark.constructionOneByOne",
//        "PersistentMapBenchmark.iteration",
//        "PersistentMapBenchmark.randomUpdate",
//        "PersistentMapBenchmark.randomLookup",
//        "PersistentMapBenchmark.removeAbsent",
//        "PersistentMapBenchmark.removePresent",
//        "PersistentSetPolymorphismBenchmark.groupByRandom",
    )
}
