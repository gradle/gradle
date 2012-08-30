/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.consumer

import org.gradle.tooling.model.DomainObjectSet

interface TestModel {
    String getName()

    TestProject getProject()

    boolean isConfigSupported()

    String getConfig(String defaultValue)

    DomainObjectSet<? extends TestProject> getChildren()
}

interface TestProject {
    String getName()
}

interface TestProtocolModel {
    String getName()

    TestProtocolProject getProject()

    Iterable<? extends TestProtocolProject> getChildren()

    String getConfig();
}

interface PartialTestProtocolModel {
    String getName()
}

interface TestProtocolProject {
    String getName()
}

class ConfigMixin {
    TestModel model

    ConfigMixin(TestModel model) {
        this.model = model
    }

    String getConfig(String value) {
        return "[${model.getConfig(value)}]"
    }

    String getName() {
        return "[${model.name}]"
    }
}