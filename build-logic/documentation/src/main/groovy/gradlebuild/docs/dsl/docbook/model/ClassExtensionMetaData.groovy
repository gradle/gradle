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
package gradlebuild.docs.dsl.docbook.model

class ClassExtensionMetaData {
    final String targetClass
    final Set<MixinMetaData> mixinClasses = []
    final Set<ExtensionMetaData> extensionClasses = []

    ClassExtensionMetaData(String targetClass) {
        this.targetClass = targetClass
    }

    def void addMixin(String plugin, String mixinClass) {
        mixinClasses.add(new MixinMetaData(plugin, mixinClass))
    }

    def void addExtension(String plugin, String extension, String extensionClass) {
        extensionClasses.add(new ExtensionMetaData(plugin, extension, extensionClass))
    }
}
