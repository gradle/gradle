/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.Project;

/**
 * A module representing a component with no unique identity.
 */
public class AnonymousModule implements Module {

    @Override
    public String getGroup() {
        return "unspecified";
    }

    @Override
    public String getName() {
        return "unspecified";
    }

    @Override
    public String getVersion() {
        return Project.DEFAULT_VERSION;
    }

    @Override
    public String getStatus() {
        return Project.DEFAULT_STATUS;
    }
}
