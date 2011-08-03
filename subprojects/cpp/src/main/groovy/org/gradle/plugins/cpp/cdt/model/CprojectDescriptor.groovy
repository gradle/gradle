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
package org.gradle.plugins.cpp.cdt.model

import org.gradle.api.internal.XmlTransformer
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject

/**
 * The actual .cproject descriptor file.
 */
class CprojectDescriptor extends XmlPersistableConfigurationObject {

    CprojectDescriptor() {
        super(new XmlTransformer())
    }

    protected String getDefaultResourceName() {
        'defaultCproject.xml'
    }

    protected void store(Node xml) {
        transformAction {
            StringBuilder xmlString = it.asString()
            xmlString.insert(xmlString.indexOf("\n") + 1, "<?fileVersion 4.0.0?>\n")
        }
    }
}