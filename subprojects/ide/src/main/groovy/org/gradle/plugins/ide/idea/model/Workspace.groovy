/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.idea.model

import org.gradle.api.internal.xml.XmlTransformer
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject

/**
 * Represents the customizable elements of an ipr (via XML hooks everything of the ipr is customizable).
 */

class Workspace extends XmlPersistableConfigurationObject {
    def Workspace(XmlTransformer withXmlActions) {
        super(withXmlActions)
    }

    @Override protected String getDefaultResourceName() {
        return 'defaultWorkspace.xml'
    }

    @Override protected void load(Node xml) {
    }

    @Override protected void store(Node xml) {
    }
}
