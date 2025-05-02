/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.resolve.http.AbstractHttpsRepoResolveIntegrationTest

class MavenHttpsRepoResolveIntegrationTest extends AbstractHttpsRepoResolveIntegrationTest {
    protected void setupRepo(boolean useAuth = false) {
        def module = mavenRepo().module('my-group', 'my-module').publish()
        if (useAuth) {
            server.allowGetOrHead('/repo1/my-group/my-module/1.0/my-module-1.0.pom', 'user', 'secret', module.pomFile)
            server.allowGetOrHead('/repo1/my-group/my-module/1.0/my-module-1.0.jar', 'user', 'secret', module.artifactFile)
        } else {
            server.allowGetOrHead('/repo1/my-group/my-module/1.0/my-module-1.0.pom', module.pomFile)
            server.allowGetOrHead('/repo1/my-group/my-module/1.0/my-module-1.0.jar', module.artifactFile)
        }
    }

    @Override
    protected String getRepoType() {
        return "maven"
    }
}
