/*
 * Copyright 2015 the original author or authors.
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

/**
 * NOTE: The sources in this package have been adopted unchanged from 'org.apache.maven:maven-ant-tasks:2.1.3'.
 *
 * These classes form part of the public Gradle API, via {@link org.gradle.api.artifacts.maven.MavenDeployer#getRepository()}.
 * We no longer use the actual Maven Ant Tasks for publishing, and these classes are only available publicly in a fat-jar that
 * bundles a version of Maven as well as the Ant tasks themselves.
 * For this reason, these classes have been 'adopted' here so they can remain in the Gradle API without bloating the Gradle distribution.
 */
package org.apache.maven.artifact.ant;