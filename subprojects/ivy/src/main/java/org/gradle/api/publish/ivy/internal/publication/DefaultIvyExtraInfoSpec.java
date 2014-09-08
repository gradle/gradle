/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.publication;

import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyExtraInfo;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.api.publish.ivy.IvyExtraInfoSpec;
import org.gradle.internal.xml.XmlValidation;

public class DefaultIvyExtraInfoSpec extends DefaultIvyExtraInfo implements IvyExtraInfoSpec {
    public DefaultIvyExtraInfoSpec() {
        super();
    }

    public void add(String namespace, String name, String value) {
        if (XmlValidation.isValidXmlName(name)) {
            extraInfo.put(new NamespaceId(namespace, name), value);
        } else {
            throw new IllegalArgumentException(String.format("Invalid ivy extra info element name: '%s'", name));
        }
    }
}
