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
    scala // <1>

    `java-library` // <2>
}

repositories {
    jcenter() // <3>
}

dependencies {
    implementation("org.scala-lang:scala-library:2.13.3") // <4>

    testImplementation("junit:junit:4.12") // <5>
    testImplementation("org.scalatest:scalatest_2.13:3.2.0")
    testImplementation("org.scalatestplus:junit-4-12_2.13:3.2.0.0")

    testRuntimeOnly("org.scala-lang.modules:scala-xml_2.13:1.2.0") // <6>
}
