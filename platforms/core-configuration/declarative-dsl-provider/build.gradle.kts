/*
 * Copyright 2023 the original author or authors.
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
    id("gradlebuild.distribution.implementation-kotlin")
}

/*configurations {
    compileClasspath {
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.LIBRARY))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements.JAR))
            attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling.SHADOWED))
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 6)
        }
    }
}*/

val artifactType = Attribute.of("artifactType", String::class.java)

dependencies {
    registerTransform(Unzip::class) {
        from.attribute(artifactType, "jar")
        to.attribute(artifactType, "java-classes-directory")
    }
}

dependencies {
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":declarative-dsl-api"))
//    api(project(":declarative-dsl-core"))
     api(project(path = ":declarative-dsl-core", configuration = "shadedRuntimeElements"))
    api(libs.futureKotlin("stdlib"))
    api(libs.inject)

    implementation(project(":model-core"))
    implementation(project(":resources"))
    implementation(libs.futureKotlin("compiler-embeddable"))
    implementation(libs.futureKotlin("reflect"))

    integTestImplementation(project(":internal-testing"))
    integTestImplementation(project(":logging"))

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
