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
package org.gradle.build.docs.dsl.docbook.model

import org.gradle.build.docs.dsl.source.model.ClassMetaData

/**
 * Represents the documentation model for extensions contributed by a given plugin.
 */
class ClassExtensionDoc {
    private final Set<ClassDoc> mixinClasses = []
    private final Map<String, ClassDoc> extensionClasses = [:]
    private final String pluginId
    final ClassMetaData targetClass
    final List<PropertyDoc> extraProperties = []
    final List<BlockDoc> extraBlocks = []

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

    Map<String, ClassDoc> getExtensionClasses() {
        return extensionClasses
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

