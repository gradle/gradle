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

package org.gradle.buildinit.plugins.internal

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.FileResolver

class GroovyLibraryProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor {

    public GroovyLibraryProjectInitDescriptor(FileResolver fileResolver, DocumentationRegistry documentationRegistry) {
        super(BuildInitTypeIds.GROOVY_LIBRARY, fileResolver, documentationRegistry);
    }

    protected Map getAdditionalBuildFileTemplateBindings() {
        return [ref_userguide_groovy_tutorial: documentationRegistry.getDocumentationFor("tutorial_groovy_projects")]
    }

    void generateProjectSources() {
        if (fileResolver.resolveFilesAsTree("src/main/groovy").empty || fileResolver.resolveFilesAsTree("src/test/groovy").empty) {
            generateClass("src/main/groovy", "Library.groovy")
            generateClass("src/test/groovy", "LibraryTest.groovy")
        }
    }
}
