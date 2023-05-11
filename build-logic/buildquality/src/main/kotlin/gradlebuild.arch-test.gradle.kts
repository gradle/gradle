/*
 * Copyright 2020 the original author or authors.
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

import com.gradle.enterprise.gradleplugin.testselection.PredictiveTestSelectionExtension
import gradlebuild.archtest.PackageCyclesExtension

plugins {
    `java-library`
    `jvm-test-suite`
    id("gradlebuild.dependency-modules")
    id("gradlebuild.code-quality")
}

val packageCyclesExtension = extensions.create<PackageCyclesExtension>("packageCycles").apply {
    excludePatterns.convention(emptyList())
}

val sharedArchTestClasses by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.CLASSES))
    }
}
notForAccessorGeneration {
    dependencies {
        sharedArchTestClasses(project(":internal-architecture-testing"))
    }
}

testing {
    suites {
        create("archTest", JvmTestSuite::class) {
            useJUnitJupiter()
            dependencies {
                implementation(project.dependencies.create(project))
                notForAccessorGeneration {
                    implementation(project.dependencies.platform(project(":distributions-dependencies")))
                    implementation(project(":internal-architecture-testing"))
                }
            }
            targets {
                all {
                    testTask.configure {
                        testClassesDirs += sharedArchTestClasses.filter { it.isDirectory }
                        classpath += sourceSets.main.get().output.classesDirs
                        systemProperty("package.cycle.exclude.patterns", packageCyclesExtension.excludePatterns.get().joinToString(","))
                        extensions.findByType<PredictiveTestSelectionExtension>()?.apply {
                            // PTS doesn't work well with architecture tests which scan all classes
                            enabled = false
                        }
                    }
                }
            }
        }
    }
}

tasks.codeQuality.configure {
    dependsOn(testing.suites.named("archTest"))
}

fun notForAccessorGeneration(runnable: Runnable) {
    if (project.name != "gradle-kotlin-dsl-accessors" && project.name != "test") {
        runnable.run()
    }
}
