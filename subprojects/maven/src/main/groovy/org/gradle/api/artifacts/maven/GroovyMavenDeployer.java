/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.artifacts.maven;

/**
 * Adds Groovy configuration convenience methods on top of the {@link MavenDeployer}.
 *
 * This class provides also a builder for repository and snapshot-repository:
 *
 * <pre>
 * mavenUploader.repository(url: 'file://repoDir') {
 *    authentication(userName: 'myName')
 *    releases(updatePolicy: 'never')
 *    snapshots(updatePolicy: 'always')
 * }
 * </pre>
 *
 * This call set the repository object and also returns an instance of this object. If you use 'snapshotRepository'
 * instead of repository, the snapshot repository is build.
 * @see MavenDeployer
 */
public interface GroovyMavenDeployer extends MavenDeployer {

}