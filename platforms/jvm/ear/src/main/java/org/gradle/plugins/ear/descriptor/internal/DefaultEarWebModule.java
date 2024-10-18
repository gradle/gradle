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
package org.gradle.plugins.ear.descriptor.internal;

import groovy.util.Node;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.provider.Property;
import org.gradle.plugins.ear.descriptor.EarWebModule;

public abstract class DefaultEarWebModule extends DefaultEarModule implements EarWebModule {

    @Override
    public abstract Property<String> getContextRoot();

    @Override
    public Node toXmlNode(Node parentModule, Object name) {
        Node web = new Node(parentModule, name);
        new Node(web, nodeNameFor("web-uri", name), getPath().get());
        new Node(web, nodeNameFor("context-root", name), getContextRoot().get());
        if (StringUtils.isNotEmpty(getAltDeployDescriptor().getOrNull())) {
            return new Node(parentModule, nodeNameFor("alt-dd", name), getAltDeployDescriptor());
        }
        return null;
    }
}
