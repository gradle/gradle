/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.build.docs.dsl.docbook

import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.build.docs.dsl.model.ClassMetaData
import org.gradle.build.docs.dsl.model.MethodMetaData
import org.gradle.build.docs.dsl.model.PropertyMetaData
import org.gradle.build.docs.dsl.model.TypeMetaData
import org.w3c.dom.Document
import org.gradle.build.docs.DomBuilder

/**
 * Represents the documentation model for extensions contributed by a given plugin.
 */
class ClassExtensionDoc {
    private final Set<ClassDoc> mixinClasses = []
    private final Map<String, ClassDoc> extensionClasses = [:]
    private final String pluginId
    private final ClassMetaData targetClass
    private final List<PropertyDoc> extraProperties = []
    private final List<BlockDoc> extraBlocks = []

    ClassExtensionDoc(String pluginId, ClassMetaData targetClass) {
        this.pluginId = pluginId
        this.targetClass = targetClass
    }

    String getPluginId() {
        return pluginId
    }

    Set<ClassDoc> getMixinClasses() {
        mixinClasses
    }

    void buildMetaData(DslDocModel model) {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        def linkRenderer = new LinkRenderer(doc, model)
        extensionClasses.each { id, type ->
            def propertyMetaData = new PropertyMetaData(id, targetClass)
            propertyMetaData.type = new TypeMetaData(type.name)

            def builder = new DomBuilder(doc, null)
            builder.para {
                text("The ")
                appendChild(linkRenderer.link(propertyMetaData.type, new DefaultGenerationListener()))
                text(" added by the ${pluginId} plugin.")
            }
            def propertyDoc = new PropertyDoc(propertyMetaData, builder.elements, [])
            extraProperties.add(propertyDoc)

            builder = new DomBuilder(doc, null)
            builder.para {
                text("Configures the ")
                appendChild(linkRenderer.link(propertyMetaData.type, new DefaultGenerationListener()))
                text(" added by the ${pluginId} plugin.")
            }
            def methodMetaData = new MethodMetaData(id, targetClass)
            methodMetaData.addParameter("configClosure", new TypeMetaData(Closure.name))
            def methodDoc = new MethodDoc(methodMetaData, builder.elements)
            extraBlocks.add(new BlockDoc(methodDoc, propertyDoc, propertyMetaData.type, false))
        }
    }

    List<PropertyDoc> getExtensionProperties() {
        def properties = mixinClasses.inject([]) {list, eClass -> eClass.classProperties.inject(list) {x, prop -> x << prop } }
        properties.addAll(extraProperties)
        return properties.sort { it.name }
    }

    List<MethodDoc> getExtensionMethods() {
        return mixinClasses.inject([]) {list, eClass -> eClass.classMethods.inject(list) {x, method -> x << method } }.sort { it.name }
    }

    List<BlockDoc> getExtensionBlocks() {
        def blocks = mixinClasses.inject([]) {list, eClass -> eClass.classBlocks.inject(list) {x, block -> x << block } }
        blocks.addAll(extraBlocks)
        return blocks.sort { it.name }
    }
}

