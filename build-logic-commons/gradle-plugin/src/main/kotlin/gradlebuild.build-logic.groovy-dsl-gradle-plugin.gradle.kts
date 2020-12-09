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
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    api(platform(project(":build-platform")))
    implementation("gradlebuild:code-quality")

    implementation(localGroovy())
    testImplementation("org.spockframework:spock-core:1.3-groovy-2.5") {
        exclude(group = "org.codehaus.groovy")
    }
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
    val vendor = System.getProperty("java.vendor")
    inputs.property("javaInstallation", "$vendor ${JavaVersion.current()}")
}

tasks.withType<Test>().configureEach {
    if (JavaVersion.current().isJava9Compatible) {
        //allow ProjectBuilder to inject legacy types into the system classloader
        jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
        jvmArgs("--illegal-access=deny")
    }
}


