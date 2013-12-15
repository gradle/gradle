/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.internal.prebuilt;

import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativebinaries.PrebuiltLibrary;
import org.gradle.nativebinaries.internal.DefaultLibrary;
import org.gradle.nativebinaries.internal.NativeBuildComponentIdentifier;

// TODO:DAZ Should not allow project specifier in requirement definition?
// TODO:DAZ Should not allow sources?
public class DefaultPrebuiltLibrary extends DefaultLibrary implements PrebuiltLibrary {

    private final DefaultSourceDirectorySet headers;

    // TODO:DAZ Should not require project here, just FileResolver
    public DefaultPrebuiltLibrary(String name, ProjectInternal project, Instantiator instantiator) {
        // TODO:DAZ It's not really a project component
        super(new NativeBuildComponentIdentifier(project.getPath(), name), instantiator);
        headers = new DefaultSourceDirectorySet("headers", project.getFileResolver());
    }

    public SourceDirectorySet getHeaders() {
        return headers;
    }
}
