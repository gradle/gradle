/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.api.internal.runtimeshaded.PackageListGenerator
import org.gradle.gradlebuild.unittestandcompile.ModuleType

val runtimeShadedPath = "$buildDir/runtime-api-info"

configurations {
    create("gradleApiRuntime") {
        isVisible = false
        isCanBeResolved = true
        isCanBeConsumed = false
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        attributes.attribute(Attribute.of("org.gradle.api", String::class.java), "runtime")
    }
    create("testKitPackages") {
        isVisible = false
        isCanBeResolved = true
        isCanBeConsumed = false
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
    }
}

dependencies {
    "gradleApiRuntime"(project(":"))
    "testKitPackages"(project(":testKit"))
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

val generateGradleApiPackageList by tasks.registering(PackageListGenerator::class) {
    classpath = configurations["gradleApiRuntime"]
    outputFile = file("$runtimeShadedPath/api-relocated.txt")
}

val generateTestKitPackageList by tasks.registering(PackageListGenerator::class) {
    classpath = configurations["testKitPackages"]
    outputFile = file("$runtimeShadedPath/test-kit-relocated.txt")
}

tasks.jar {
    into("org/gradle/api/internal/runtimeshaded") {
        from(generateGradleApiPackageList)
        from(generateTestKitPackageList)
    }
}
