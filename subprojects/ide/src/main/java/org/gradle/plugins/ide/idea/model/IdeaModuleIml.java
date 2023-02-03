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
package org.gradle.plugins.ide.idea.model;

import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.api.XmlFileContentMerger;

import java.io.File;

/**
 * Models the generation/parsing/merging capabilities of an IDEA module.
 * <p>
 * For examples, see docs for {@link IdeaModule}.
 */
public class IdeaModuleIml extends XmlFileContentMerger {

    private File generateTo;

    public IdeaModuleIml(XmlTransformer xmlTransformer, File generateTo) {
        super(xmlTransformer);
        this.generateTo = generateTo;
    }

    /**
     * Folder where the *.iml file will be generated to
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    public File getGenerateTo() {
        return generateTo;
    }

    public void setGenerateTo(File generateTo) {
        this.generateTo = generateTo;
    }
}
