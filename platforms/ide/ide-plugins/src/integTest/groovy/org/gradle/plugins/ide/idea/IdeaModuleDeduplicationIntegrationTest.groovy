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

package org.gradle.plugins.ide.idea

import org.gradle.plugins.ide.AbstractIdeDeduplicationIntegrationTest

class IdeaModuleDeduplicationIntegrationTest extends AbstractIdeDeduplicationIntegrationTest {

    @Override
    protected String getIdeName() {
        return "idea"
    }

    @Override
    protected String getConfiguredModule() {
        return "idea.module"
    }

    @Override
    protected String projectName(String path) {
        File iml = file(path).listFiles().find { it.name.endsWith("iml") }
        assert iml != null
        return iml.name - ".iml"
    }
}
