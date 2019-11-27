/*
 * Copyright 2019 the original author or authors.
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
    `java-library`
}

// tag::declare-outgoing-configuration[]
val instrumentedJars by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
    // If you want this configuration to share the same dependencies, otherwise omit this line
    extendsFrom(configurations["implementation"], configurations["runtimeOnly"])
}
// end::declare-outgoing-configuration[]

val instrumentedJar = tasks.register("instrumentedJar", Jar::class) {
    archiveClassifier.set("instrumented")
}

// tag::attach-outgoing-artifact[]
artifacts {
    add("instrumentedJars", instrumentedJar)
}
// end::attach-outgoing-artifact[]

/*
// tag::attach-outgoing-artifact-explicit[]
artifacts {
    add("instrumentedJars", someTask.outputFile) {
        builtBy(someTask)
    }
}
// end::attach-outgoing-artifact-explicit[]
*/

