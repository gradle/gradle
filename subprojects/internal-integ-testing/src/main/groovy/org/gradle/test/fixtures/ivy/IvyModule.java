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

package org.gradle.test.fixtures.ivy;

import org.gradle.util.TestFile;

import java.util.Map;

public interface IvyModule {
    TestFile getIvyFile();

    TestFile getJarFile();

    /**
     * Don't publish an ivy.xml for this module.
     */
    IvyModule withNoMetaData();

    IvyModule withStatus(String status);

    IvyModule dependsOn(String organisation, String module, String revision);

    IvyModule artifact(Map<String, ?> options);

    /**
     * Publishes ivy.xml plus all artifacts with different content to previous publication.
     */
    IvyModule publishWithChangedContent();

    /**
     * Publishes ivy.xml plus all artifacts
     */
    IvyModule publish();

    IvyDescriptor getIvy();
}
