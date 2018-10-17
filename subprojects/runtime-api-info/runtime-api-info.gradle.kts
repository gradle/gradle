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

dependencies {
    compile(project(":distributionsDependencies"))
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

val gradleApi by configurations.creating {
    isVisible = false
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    attributes.attribute(Attribute.of("org.gradle.api", String::class.java), "yes")
}

dependencies {
    listOf(":", ":core", ":dependencyManagement", ":pluginUse", ":toolingApi").forEach {
        add(gradleApi.name, project(it))
    }
}

val generateGradleApiPackageList = tasks.register<PackageListGenerator>("generateGradleApiPackageList") {
    classpath = gradleApi
    outputFile = file("$runtimeShadedPath/api-relocated.txt")
}

val generateTestKitPackageList = tasks.register<PackageListGenerator>("generateTestKitPackageList") {
    classpath = project(":testKit").configurations["compileClasspath"] // TODO:kotlin-dsl revert to accessor
    outputFile = file("$runtimeShadedPath/test-kit-relocated.txt")
}

tasks.named<Jar>("jar") {
    into("org/gradle/api/internal/runtimeshaded") {
        from(generateGradleApiPackageList)
        from(generateTestKitPackageList)
    }
}
