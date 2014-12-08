/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugins.ide.eclipse

import org.gradle.plugins.ide.AbstractIdeIntegrationSpec

class AbstractEclipseIntegrationSpec extends AbstractIdeIntegrationSpec {
    protected EclipseClasspathFixture getClasspath() {
        return new EclipseClasspathFixture(testDirectory, executer.gradleUserHomeDir)
    }

    protected EclipseClasspathFixture classpath(String project) {
        return new EclipseClasspathFixture(testDirectory.file(project), executer.gradleUserHomeDir)
    }

    protected EclipseWtpComponentFixture getWtpComponent() {
        return new EclipseWtpComponentFixture(testDirectory)
    }

    protected EclipseWtpComponentFixture wtpComponent(String project) {
        return new EclipseWtpComponentFixture(testDirectory.file(project))
    }

    protected EclipseWtpFacetsFixture getWtpFacets() {
        return new EclipseWtpFacetsFixture(testDirectory)
    }

    protected EclipseWtpFacetsFixture wtpFacets(String project) {
        return new EclipseWtpFacetsFixture(testDirectory.file(project))
    }

    protected EclipseProjectFixture getProject() {
        return new EclipseProjectFixture(testDirectory)
    }

    protected EclipseProjectFixture project(String project) {
        return new EclipseProjectFixture(testDirectory.file(project))
    }
}
