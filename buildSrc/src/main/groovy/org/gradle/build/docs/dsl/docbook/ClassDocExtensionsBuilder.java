/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.build.docs.dsl.docbook;

import org.gradle.build.docs.dsl.docbook.model.ExtensionMetaData;
import org.gradle.build.docs.dsl.docbook.model.MixinMetaData;

import java.util.HashMap;
import java.util.Map;

public class ClassDocExtensionsBuilder {
    private final DslDocModel model;

    public ClassDocExtensionsBuilder(DslDocModel model) {
        this.model = model;
    }

    public void build(ClassDoc classDoc) {
        Map<String, ClassExtensionDoc> plugins = new HashMap<String, ClassExtensionDoc>();
        for (MixinMetaData mixin : classDoc.getExtensionMetaData().getMixinClasses()) {
            String pluginId = mixin.getPluginId();
            ClassExtensionDoc classExtensionDoc = plugins.get(pluginId);
            if (classExtensionDoc == null) {
                classExtensionDoc = new ClassExtensionDoc(pluginId, classDoc.getClassMetaData());
                plugins.put(pluginId, classExtensionDoc);
            }
            classExtensionDoc.getMixinClasses().add(model.getClassDoc(mixin.getMixinClass()));
        }
        for (ExtensionMetaData extension : classDoc.getExtensionMetaData().getExtensionClasses()) {
            String pluginId = extension.getPluginId();
            ClassExtensionDoc classExtensionDoc = plugins.get(pluginId);
            if (classExtensionDoc == null) {
                classExtensionDoc = new ClassExtensionDoc(pluginId, classDoc.getClassMetaData());
                plugins.put(pluginId, classExtensionDoc);
            }
            classExtensionDoc.getExtensionClasses().put(extension.getExtensionId(), model.getClassDoc(extension.getExtensionClass()));
        }
        for (ClassExtensionDoc extensionDoc : plugins.values()) {
            extensionDoc.buildMetaData(model);
            classDoc.addClassExtension(extensionDoc);
        }
    }
}
