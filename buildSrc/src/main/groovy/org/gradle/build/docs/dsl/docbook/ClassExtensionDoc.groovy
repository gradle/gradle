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

class ClassExtensionDoc {
    private final List<ClassDoc> extensionClass
    private final String pluginId

    ClassExtensionDoc(String pluginId, List<ClassDoc> extensionClass) {
        this.pluginId = pluginId
        this.extensionClass = extensionClass
    }

    String getPluginId() {
        return pluginId
    }

    List<ClassDoc> getExtensionClasses() {
        extensionClass
    }

    List<PropertyDoc> getExtensionProperties() {
        return extensionClass.inject([]) {list, eClass -> eClass.classProperties.inject(list) {x, prop -> x << prop } }
    }

    List<MethodDoc> getExtensionMethods() {
        return extensionClass.inject([]) {list, eClass -> eClass.classMethods.inject(list) {x, method -> x << method } }
    }

    List<BlockDoc> getExtensionBlocks() {
        return extensionClass.inject([]) {list, eClass -> eClass.classBlocks.inject(list) {x, block -> x << block } }
    }
}

