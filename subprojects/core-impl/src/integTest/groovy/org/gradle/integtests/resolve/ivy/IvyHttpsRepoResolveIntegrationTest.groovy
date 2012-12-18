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

package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.resolve.http.AbstractHttpsRepoResolveIntegrationTest

class IvyHttpsRepoResolveIntegrationTest extends AbstractHttpsRepoResolveIntegrationTest {
    protected String setupRepo() {
        def module = ivyRepo().module('my-group', 'my-module').publish()
        server.allowGetOrHead('/repo1/my-group/my-module/1.0/ivy-1.0.xml', module.ivyFile)
        server.allowGetOrHead('/repo1/my-group/my-module/1.0/my-module-1.0.jar', module.jarFile)
        "ivy"
    }
}
