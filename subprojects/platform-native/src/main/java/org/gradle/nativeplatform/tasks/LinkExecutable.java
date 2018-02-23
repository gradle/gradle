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
package org.gradle.nativeplatform.tasks;

import org.gradle.api.Incubating;
import org.gradle.nativeplatform.internal.DefaultLinkerSpec;
import org.gradle.nativeplatform.internal.LinkerSpec;

/**
 * Links a binary executable from object files and libraries.
 */
@Incubating
public class LinkExecutable extends AbstractLinkTask {
    @Override
    protected LinkerSpec createLinkerSpec() {
        return new DefaultLinkerSpec();
    }
}
