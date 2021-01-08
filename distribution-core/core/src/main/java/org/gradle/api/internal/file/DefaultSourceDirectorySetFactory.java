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

package org.gradle.api.internal.file;

import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.deprecation.DeprecationLogger;

@SuppressWarnings("deprecation")
public class DefaultSourceDirectorySetFactory implements SourceDirectorySetFactory {
    private final ObjectFactory objectFactory;

    public DefaultSourceDirectorySetFactory(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    @Override
    public DefaultSourceDirectorySet create(String name) {
        deprecate();
        return (DefaultSourceDirectorySet) objectFactory.sourceDirectorySet(name, name);
    }

    @Override
    public DefaultSourceDirectorySet create(String name, String displayName) {
        deprecate();
        return (DefaultSourceDirectorySet) objectFactory.sourceDirectorySet(name, displayName);
    }

    private static void deprecate() {
        DeprecationLogger.deprecateInternalApi("SourceDirectorySetFactory")
            .replaceWith("ObjectFactory.sourceDirectorySet(String, String)")
            .willBeRemovedInGradle8()
            .withUserManual("lazy_configuration", "property_files_api_reference")
            .nagUser();
    }
}
