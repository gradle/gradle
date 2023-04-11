/*
 * Copyright 2018 the original author or authors.
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
import gradlebuild.jmh.tasks.JmhHTMLReport

plugins {
    id("me.champeau.jmh")
}

configurations {
    jmhImplementation {
        extendsFrom(configurations.implementation.get())
        configurations.findByName("platformImplementation")?.let {
            extendsFrom(it)
        }
    }
    jmhRuntimeClasspath {
        attributes {
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        }
    }
}

jmh {
    includeTests = false
    resultFormat = "CSV"
}

val jmhReport = tasks.register<JmhHTMLReport>("jmhReport") {
    group = "jmh"
    csv = tasks.jmh.map { layout.buildDirectory.file("results/jmh/results.csv").get() }
    destination = layout.buildDirectory.dir("reports/jmh-html")
}
