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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import groovy.lang.Closure;
import groovy.util.Node;
import groovy.util.NodeList;
import groovy.xml.QName;
import org.gradle.api.Incubating;

/**
 * Gradle model element, the configurable parts of the .project file.
 */
@Incubating
public class ProjectSettings {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Apply this logical model to the physical descriptor
     */
    public void applyTo(ProjectDescriptor descriptor) {
        descriptor.getOrCreate("name").setValue(name);

        // not anywhere close to right at all, very hardcoded.
        Node genMakeBuilderBuildCommand = descriptor.findBuildCommand(new Closure<Boolean>(this, this) {
            public Boolean doCall(Node it) {
                Node name = (Node) it.getAt(new QName("name")).get(0);
                return name.text().equals("org.eclipse.cdt.managedbuilder.core.genmakebuilder");
            }

        });
        if (genMakeBuilderBuildCommand != null) {
            Node arguments = (Node) genMakeBuilderBuildCommand.getAt(new QName("arguments")).get(0);
            NodeList dictionary = arguments.getAt(new QName("dictionary"));
            Node buildLocation = (Node) Iterables.find(dictionary, new Predicate() {
                @Override
                public boolean apply(Object entry) {
                    Node key = (Node) ((Node) entry).getAt(new QName("key")).get(0);
                    return key.text().equals("org.eclipse.cdt.make.core.buildLocation");
                }
            });
            if (buildLocation != null) {
                Node value = (Node) buildLocation.getAt(new QName("value")).get(0);
                value.setValue("${workspace_loc:/" + name + "/Debug}");
            }

        }


    }

}
