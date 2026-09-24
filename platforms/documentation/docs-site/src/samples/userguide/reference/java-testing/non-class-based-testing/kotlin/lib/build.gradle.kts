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
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::test-setup[]
testing.suites.named("test", JvmTestSuite::class) {
    useJUnitJupiter() // <1>

    dependencies {
        runtimeOnly(project(":test-engine")) // <2>
    }

    targets.all {
        testTask.configure {
            testDefinitionDirs.from("src/test/definitions") // <3>
        }
    }
}
// end::test-setup[]
