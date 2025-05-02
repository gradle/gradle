/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.ide.xcode.tasks.internal;

import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject;

import java.util.HashMap;
import java.util.Map;

public class XcodeWorkspaceFile extends XmlPersistableConfigurationObject {
    public XcodeWorkspaceFile(XmlTransformer transformer) {
        super(transformer);
    }

    @Override
    protected String getDefaultResourceName() {
        return "default.xcworkspacedata";
    }

    public void addLocation(String location) {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("location", "absolute:" + location);
        getXml().appendNode("FileRef", attributes);
    }

}
