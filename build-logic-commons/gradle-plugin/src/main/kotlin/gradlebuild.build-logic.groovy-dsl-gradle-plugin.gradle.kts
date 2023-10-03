import gradlebuild.commons.configureJavaToolChain

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

plugins {
    id("java-library")
    id("groovy-gradle-plugin")
    id("gradlebuild.code-quality")
    id("gradlebuild.ci-reporting")
    id("gradlebuild.test-retry")
}

java.configureJavaToolChain()

dependencies {
    api(platform("gradlebuild:build-platform"))
    implementation("gradlebuild:gradle-plugin")

    implementation(localGroovy())
    testImplementation("org.spockframework:spock-core")
    testImplementation("net.bytebuddy:byte-buddy")
    testImplementation("org.objenesis:objenesis")
}

tasks.withType<GroovyCompile>().configureEach {
    groovyOptions.apply {
        encoding = "utf-8"
        forkOptions.jvmArgs?.add("-XX:+HeapDumpOnOutOfMemoryError")
    }
    options.apply {
        isFork = true
        encoding = "utf-8"
        compilerArgs = mutableListOf("-Xlint:-options", "-Xlint:-path")
    }
}

tasks.withType<Test>().configureEach {
    if (JavaVersion.current().isJava9Compatible) {
        //allow ProjectBuilder to inject legacy types into the system classloader
        jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
        jvmArgs("--illegal-access=deny")
    }
    useJUnitPlatform()
}


