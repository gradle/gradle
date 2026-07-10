/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.docs.dsl.docbook;

import groovy.lang.Closure;
import gradlebuild.docs.dsl.docbook.model.*;
import gradlebuild.docs.dsl.source.model.MethodMetaData;
import gradlebuild.docs.dsl.source.model.PropertyMetaData;
import gradlebuild.docs.dsl.source.model.TypeMetaData;
import org.gradle.internal.UncheckedException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ClassDocExtensionsBuilder {
    private final DslDocModel model;
    private final GenerationListener listener;

    public ClassDocExtensionsBuilder(DslDocModel model, GenerationListener listener) {
        this.model = model;
        this.listener = listener;
    }

    /**
     * Builds the extension meta-data for the given class.
     */
    public void build(ClassDoc classDoc) {
        Map<String, ClassExtensionDoc> plugins = new HashMap<String, ClassExtensionDoc>();
        for (MixinMetaData mixin : classDoc.getExtensionMetaData().getMixinClasses()) {
            String pluginId = mixin.getPluginId();
            ClassExtensionDoc classExtensionDoc = plugins.get(pluginId);
            if (classExtensionDoc == null) {
                classExtensionDoc = new ClassExtensionDoc(pluginId, classDoc);
                plugins.put(pluginId, classExtensionDoc);
            }
            classExtensionDoc.getMixinClasses().add(model.getClassDoc(mixin.getMixinClass()));
        }
        for (ExtensionMetaData extension : classDoc.getExtensionMetaData().getExtensionClasses()) {
            String pluginId = extension.getPluginId();
            ClassExtensionDoc classExtensionDoc = plugins.get(pluginId);
            if (classExtensionDoc == null) {
                classExtensionDoc = new ClassExtensionDoc(pluginId, classDoc);
                plugins.put(pluginId, classExtensionDoc);
            }
            classExtensionDoc.getExtensionClasses().put(extension.getExtensionId(), model.getClassDoc(extension.getExtensionClass()));
        }
        for (ClassExtensionDoc extensionDoc : plugins.values()) {
            build(extensionDoc);
            classDoc.addClassExtension(extensionDoc);
        }
    }

    private void build(ClassExtensionDoc extensionDoc) {
        Document doc;
        try {
            doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        LinkRenderer linkRenderer = new LinkRenderer(doc, model);
        for (Map.Entry<String, ClassDoc> entry : extensionDoc.getExtensionClasses().entrySet()) {
            String id = entry.getKey();
            ClassDoc type = entry.getValue();
            PropertyMetaData propertyMetaData = new PropertyMetaData(id, extensionDoc.getTargetClass().getClassMetaData());
            propertyMetaData.setType(new TypeMetaData(type.getName()));

            Element para = doc.createElement("para");
            para.appendChild(doc.createTextNode("The "));
            para.appendChild(linkRenderer.link(propertyMetaData.getType(), listener));
            para.appendChild(doc.createTextNode(String.format(" added by the %s plugin.", extensionDoc.getPluginId())));

            PropertyDoc propertyDoc = new PropertyDoc(propertyMetaData, Collections.singletonList(para), Collections.<ExtraAttributeDoc>emptyList());
            extensionDoc.getExtraProperties().add(propertyDoc);

            para = doc.createElement("para");
            para.appendChild(doc.createTextNode("Configures the "));
            para.appendChild(linkRenderer.link(propertyMetaData.getType(), listener));
            para.appendChild(doc.createTextNode(String.format(" added by the %s plugin.", extensionDoc.getPluginId())));

            MethodMetaData methodMetaData = new MethodMetaData(id, extensionDoc.getTargetClass().getClassMetaData());
            methodMetaData.addParameter("configClosure", new TypeMetaData(Closure.class.getName()));
            MethodDoc methodDoc = new MethodDoc(methodMetaData, Collections.singletonList(para));
            extensionDoc.getExtraBlocks().add(new BlockDoc(methodDoc, propertyDoc, propertyMetaData.getType(), false));
        }
    }
}
