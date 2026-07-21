/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.tasks.javadoc.Javadoc

plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.publish-public-libraries")
    id("xdcl-gradle-plugin")
    id("gradlebuild.xdcl-ecosystem-library")
}

description = "Built-in XDCL Groovy ecosystem: schema (groovy.xdsl) + generated facades + the GroovyLibraryModel runtime build model. A sibling of the JVM ecosystem (shares the common dependency/repository schema). Published for plugin authors and shipped in the distribution (prototype)"

// The facades are generated build artifacts (regenerated every build); keep the style/header and the
// (published) javadoc gates off them — the schema's `///` docs aren't valid Javadoc.
tasks.withType<Checkstyle>().configureEach {
    exclude { it.file.absolutePath.contains("/generated/xdcl/") }
}
tasks.withType<Javadoc>().configureEach {
    exclude { it.file.absolutePath.contains("/generated/xdcl/") }
}

// A schema+model library: groovy.xdsl (packed under META-INF/xdcl/ by xdcl-gradle-plugin), the facades
// generated from it, and the GroovyLibraryModel/GroovyClasses runtime build model (public Gradle API
// only, so compileOnly(gradleApi) keeps them off the published/gate-checked variants). The reaction
// that activates it lives in the sibling carrier :xdcl-groovy-ecosystem-plugin.
dependencies {
    // groovy.xdsl `import`s org.gradle.demos.common.dsl (Dependencies/Repository + HasDependencies/
    // HasRepositories traits the GroovyLibrary template composes), and GroovyLibraryModel references the
    // common TestReports. `api` so consumers see the common facades; also how xdclCodegen resolves the
    // import (importedSchemaDirectories). Published, so the externally-available gate is satisfied.
    api(projects.xdclCommonEcosystem)
}
