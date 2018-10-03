import org.gradle.gradlebuild.unittestandcompile.ModuleType

/*
 * Copyright 2014 the original author or authors.
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

/*
 * Groovy specific adaptions to the model management.
 */
plugins {
    `java-library`
    id("gradlebuild.strict-compile")
    id("gradlebuild.classycle")
}

dependencies {
    api(project(":baseServices"))
    api(project(":modelCore"))
    api(library("groovy"))

    implementation(project(":baseServicesGroovy"))
    implementation(library("jcip"))
    implementation(library("guava"))
}

gradlebuildJava {
    moduleType = ModuleType.ENTRY_POINT
}

testFixtures {
    from(":core")
    from(":modelCore")
}
