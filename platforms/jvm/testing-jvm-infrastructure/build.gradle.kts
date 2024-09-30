/*
 * Copyright 2022 the original author or authors.
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
    id("gradlebuild.distribution.api-java")
}

gradlebuildJava.usedInWorkers()

description = """JVM-specific test infrastructure, including support for bootstrapping and configuring test workers
and executing tests.
Few projects should need to depend on this module directly. Most external interactions with this module are through the
various implementations of WorkerTestClassProcessorFactory.
"""

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.time)
    api(projects.baseServices)
    api(projects.messaging)
    api(projects.testingBaseInfrastructure)

    api(libs.jsr305)
    api(libs.junit)
    api(libs.testng)
    api(libs.bsh) {
        because("""We need to create a capability conflict between "org.beanshell:bsh", and "org.beanshell:beanshell" by explicitly including this lib
            version of bsh, instead of depending on the transitive version contributed by testng.  This lib contributes the "beanshell" capability,
            and the conflict resolution rules from capabilities.json ensures this is the version that is resolved.

            This is necessary because the beanshell project migrated coordinates from org.beanshell in version 2.0b4 to org.apache-extras.beanshell
            in version 2.0b5.  We want to resolve version 2.0b6.  The conflict ensures org.apache-extras.beanshell is selected, so we get 2.0b6.  If
            we don't do this, we get 2.0b4, which is not present in our verification-metadata.xml file and causes a build failure.
        """.trimMargin())
    }

    implementation(projects.concurrent)

    implementation(libs.slf4jApi)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.messaging))
    testImplementation(libs.assertj) {
        because("We test assertion errors coming from AssertJ")
    }
    testImplementation("org.opentest4j:opentest4j") {
        version {
            // MultipleFailuresError appears only since 1.3.0-RC2
            require("1.3.0")
        }
        because("We test assertion errors coming from OpenTest4J")
    }
    testRuntimeOnly(libs.guice) {
        because("Used by TestNG")
    }

    testFixturesImplementation(projects.testingBase)
    testFixturesImplementation(libs.junit)
    testFixturesImplementation(libs.testng)
    testFixturesImplementation(libs.bsh)
}

dependencyAnalysis {
    issues {
        onAny() {
            // Bsh is not used directly, but is selected as the result of capabilities conflict resolution - the classes ARE required at runtime by TestNG
            exclude(libs.bsh)
        }
    }
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
