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

package org.gradle.buildinit.plugins.internal

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.FileResolver

class JavaLibraryProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor {

    public JavaLibraryProjectInitDescriptor(DefaultTemplateLibraryVersionProvider libraryVersionProvider, FileResolver fileResolver, DocumentationRegistry documentationRegistry) {
        super(BuildInitTypeIds.JAVA_LIBRARY, "java", libraryVersionProvider, fileResolver, documentationRegistry);
    }

    protected Map getAdditionalBuildFileTemplateBindings() {
        return [groovyAllVersion: libraryVersionProvider.getVersion("groovy-all"),
                junitVersion: libraryVersionProvider.getVersion("junit"),
                ref_userguide_java_tutorial: documentationRegistry.getDocumentationFor("tutorial_java_projects")]
    }

    void generateProjectSources() {
        generateClass("src/main/java", "Library.java")
        generateClass("src/test/java", "LibraryTest.java")
    }

}