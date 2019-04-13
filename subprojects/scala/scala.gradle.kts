import org.gradle.gradlebuild.unittestandcompile.ModuleType

/*
 * Copyright 2010 the original author or authors.
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

dependencies {
    compile(library("groovy"))

    compile(project(":core"))
    compile(project(":languageJvm"))
    compile(project(":languageScala"))
    compile(project(":plugins"))

    testCompile(library("slf4j_api"))

    integTestRuntime(project(":ide"))
    integTestRuntime(project(":maven"))
}

gradlebuildJava {
    // Needs to run in the compiler daemon
    moduleType = ModuleType.WORKER
}

testFixtures {
    from(":plugins") // include core test fixtures
    from(":languageJvm")
    from(":languageScala")
}

tasks.named<Test>("integTest") {
    jvmArgs("-XX:MaxPermSize=1500m") // AntInProcessScalaCompilerIntegrationTest needs lots of permgen
}
