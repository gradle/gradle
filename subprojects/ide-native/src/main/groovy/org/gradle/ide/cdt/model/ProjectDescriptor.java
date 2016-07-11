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
package org.gradle.ide.cdt.model;

import groovy.lang.Closure;
import groovy.util.Node;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Incubating;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject;

import java.util.List;

/**
 * The actual .project descriptor file.
 */
@Incubating
public class ProjectDescriptor extends XmlPersistableConfigurationObject {
    public ProjectDescriptor() {
        super(new XmlTransformer());
    }

    protected String getDefaultResourceName() {
        return "defaultProject.xml";
    }

    public Node getOrCreate(String name) {
        return getOrCreate(getXml(), name);
    }

    public Node findBuildCommand(Closure predicate) {
        return DefaultGroovyMethods.find(XmlPersistableConfigurationObject.getChildren(XmlPersistableConfigurationObject.findFirstChildNamed(getXml(), "buildSpec"), "buildCommand"), predicate);
    }

    public Node getOrCreate(Node parent, String name) {
        List<Node> node = XmlPersistableConfigurationObject.getChildren(parent, name);
        return !node.isEmpty() ? node.get(0) : parent.appendNode(name);
    }

}
