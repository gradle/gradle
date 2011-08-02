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
 * The actual .project descriptor file.
 */
class ProjectDescriptor extends XmlPersistableConfigurationObject {

    ProjectDescriptor() {
        super(new XmlTransformer())
    }

    protected String getDefaultResourceName() {
        'defaultProject.xml'
    }

    void setName(String name) {
        getOrCreate("name").value = name
        
        // not anywhere close to right at all, very hardcoded.
        def genMakeBuilderBuildCommand = findBuildCommand { it.name[0].text() == "org.eclipse.cdt.managedbuilder.core.genmakebuilder" }
        if (genMakeBuilderBuildCommand) {
            def dict = genMakeBuilderBuildCommand.arguments[0].dictionary.find { it.key[0].text() == "org.eclipse.cdt.make.core.buildLocation" }
            if (dict) {
                dict.value[0].value = "\${workspace_loc:/$name/Debug}"
            }
        }
    }

    private getOrCreate(String name) {
        getOrCreate(xml, name)
    }

    private findBuildCommand(Closure predicate) {
        xml.buildSpec[0].buildCommand.find(predicate)
    }

    private getOrCreate(Node parent, String name) {
        def node = parent.get(name)
        node ? node.first() : parent.appendNode(name)
    }
}