/*
 * Copyright 2019 the original author or authors.
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
    `java-library`
}


repositories {
    mavenCentral()
}

// tag::test_dependency[]
dependencies {
    testImplementation("junit:junit:4.12")
    testImplementation(project(":producer"))
}
// end::test_dependency[]

// tag::ask-for-instrumented-classes[]
configurations {
    testRuntimeClasspath {
        attributes {
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, "instrumented-jar"))
        }
    }
}
// end::ask-for-instrumented-classes[]

// tag::compatibility-rule-use[]
dependencies {
    attributesSchema {
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE) {
            compatibilityRules.add(InstrumentedJarsRule::class.java)
        }
    }
}
// end::compatibility-rule-use[]

// tag::compatibility-rule[]
open class InstrumentedJarsRule: AttributeCompatibilityRule<LibraryElements> {

    override fun execute(details: CompatibilityCheckDetails<LibraryElements>) = details.run {
        if (consumerValue?.name == "instrumented-jar" && producerValue?.name == "jar") {
            compatible()
        }
    }
}
// end::compatibility-rule[]

tasks.register("showTestClasspath") {
    inputs.files(configurations.testCompileClasspath)
    inputs.files(configurations.testRuntimeClasspath)
    doLast {
        println(configurations.testCompileClasspath.get().files.map(File::name))
        println(configurations.testRuntimeClasspath.get().files.map(File::name))
    }
}
