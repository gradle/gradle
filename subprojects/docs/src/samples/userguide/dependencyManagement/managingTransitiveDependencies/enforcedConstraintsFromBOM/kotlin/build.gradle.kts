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

plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::dependency-on-bom[]
dependencies {
    // import a BOM. The versions used in this file will override any other version found in the graph
    implementation(enforcedPlatform("org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE"))

    // define dependencies without versions
    implementation("com.google.code.gson:gson")
    implementation("dom4j:dom4j")

    // this version will be overriden by the one found in the BOM
    implementation("org.codehaus.groovy:groovy:1.8.6")
}
// end::dependency-on-bom[]

//Note: dom4j also brings in xml-apis as transitive dependency
tasks.register<Copy>("copyLibs") {
    from(configurations.compileClasspath)
    into("$buildDir/libs")
}
