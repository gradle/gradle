import org.gradle.gradlebuild.unittestandcompile.ModuleType

/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"));
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

val css by configurations.creating {
    // define a configuration that, when resolved, will look in
    // the producer for a publication that exposes CSS resources
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named("css-resources"))
    }
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    compile(library("groovy"))

    compile(project(":resources"))
    compile(project(":core"))
    compile(project(":toolingApi"))
    compile(project(":reporting"))
    compile(project(":plugins"))
    compile(project(":ear"))
    compile(library("guava"))
    compile(library("slf4j_api"))

    testCompile(testLibrary("jsoup"))

    integTestRuntime(project(":toolingApiBuilders"))

    css(project(":docs"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
}

tasks.named<Copy>("processResources") {
    from(css) {
        into("org/gradle/api/plugins/buildcomparison/render/internal/html")
        include("base.css")
    }
}
