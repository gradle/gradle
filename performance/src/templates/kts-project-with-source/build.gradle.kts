/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.JavaVersion
import org.gradle.api.tasks.diagnostics.DependencyReportTask
import org.gradle.api.tasks.testing.Test
import org.gradle.plugins.ide.idea.IdeaPlugin
import java.io.File

apply<IdeaPlugin>()
apply(plugin = "java")
apply(plugin = "eclipse")

repositories {
    mavenCentral()
}

dependencies {
    implementation("commons-lang:commons-lang:2.5")
    implementation("commons-httpclient:commons-httpclient:3.0")
    implementation("commons-codec:commons-codec:1.2")
    implementation("org.slf4j:jcl-over-slf4j:1.7.10")
    implementation("org.codehaus.groovy:groovy:2.4.15")
    testImplementation("junit:junit:4.13")
    runtimeOnly("com.googlecode:reflectasm:1.01")
}

(tasks.getByName("test") as Test).apply {
    if (!JavaVersion.current().isJava8Compatible) {
        jvmArgs("-XX:MaxPermSize=512m")
    }
    jvmArgs("-XX:+HeapDumpOnOutOfMemoryError")
}

task<DependencyReportTask>("dependencyReport") {
    outputs.upToDateWhen { false }
    outputFile = File(buildDir, "dependencies.txt")
}
