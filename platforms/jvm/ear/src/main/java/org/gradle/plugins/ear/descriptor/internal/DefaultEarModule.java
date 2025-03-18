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

import com.google.common.base.Objects;
import groovy.namespace.QName;
import groovy.util.Node;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.provider.Property;
import org.gradle.plugins.ear.descriptor.EarModule;

public abstract class DefaultEarModule implements EarModule {

    @Override
    public abstract Property<String> getPath();

    @Override
    public abstract Property<String> getAltDeployDescriptor();

    @Override
    public Node toXmlNode(Node parentModule, Object name) {
        Node node = new Node(parentModule, name, getPath().get());
        String altDeployDescriptor = getAltDeployDescriptor().getOrNull();
        if (StringUtils.isNotEmpty(altDeployDescriptor)) {
            new Node(parentModule, nodeNameFor("alt-dd", name), altDeployDescriptor);
        }
        return node;
    }

    protected Object nodeNameFor(String name, Object sampleName) {
        if (sampleName instanceof QName) {
            return new QName(((QName) sampleName).getNamespaceURI(), name);
        }
        return name;
    }

    @Override
    public int hashCode() {
        String path = getPath().getOrNull();
        String altDeployDescriptor = getAltDeployDescriptor().getOrNull();
        int result;
        result = path != null ? path.hashCode() : 0;
        result = 31 * result + (altDeployDescriptor != null ? altDeployDescriptor.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultEarModule)) {
            return false;
        }
        String path = getPath().getOrNull();
        String altDeployDescriptor = getAltDeployDescriptor().getOrNull();
        String thatPath = ((DefaultEarModule) o).getPath().getOrNull();
        String thatAltDeployDescriptor = ((DefaultEarModule) o).getAltDeployDescriptor().getOrNull();
        return Objects.equal(path, thatPath) && Objects.equal(altDeployDescriptor, thatAltDeployDescriptor);
    }
}
