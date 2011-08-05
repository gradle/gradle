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

/**
 * Represents the documentation model for extensions contributed by a given plugin.
 */
class ClassExtensionDoc {
    private final Set<ClassDoc> mixinClasses = []
    private final Map<String, ClassDoc> extensionClasses = [:]
    private final String pluginId

    ClassExtensionDoc(String pluginId) {
        this.pluginId = pluginId
    }

    String getPluginId() {
        return pluginId
    }

    Set<ClassDoc> getMixinClasses() {
        mixinClasses
    }

    List<PropertyDoc> getExtensionProperties() {
        return mixinClasses.inject([]) {list, eClass -> eClass.classProperties.inject(list) {x, prop -> x << prop } }.sort { it.name }
    }

    List<MethodDoc> getExtensionMethods() {
        return mixinClasses.inject([]) {list, eClass -> eClass.classMethods.inject(list) {x, method -> x << method } }.sort { it.name }
    }

    List<BlockDoc> getExtensionBlocks() {
        return mixinClasses.inject([]) {list, eClass -> eClass.classBlocks.inject(list) {x, block -> x << block } }.sort { it.name }
    }
}

