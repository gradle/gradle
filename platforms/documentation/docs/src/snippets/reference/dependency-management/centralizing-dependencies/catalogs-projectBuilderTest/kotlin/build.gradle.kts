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
    `java-gradle-plugin`
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:4.6.1")
}

// Needed when using ProjectBuilder
class AddOpensArgProvider(private val test: Test) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> {
        return if (test.javaVersion.isCompatibleWith(JavaVersion.VERSION_1_9)) {
            listOf("--add-opens=java.base/java.lang=ALL-UNNAMED")
        } else {
            emptyList()
        }
    }
}
tasks.withType<Test>().configureEach {
    jvmArgumentProviders.add(AddOpensArgProvider(this))
}
