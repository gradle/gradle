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

/**
 * Represents the documentation model for extensions contributed by a given plugin.
 */
class ClassExtensionDoc {
    private final Set<ClassDoc> mixinClasses = []
    private final Map<String, ClassDoc> extensionClasses = [:]
    private final String pluginId
    final ClassDoc targetClass
    final List<PropertyDoc> extraProperties = []
    final List<BlockDoc> extraBlocks = []

    ClassExtensionDoc(String pluginId, ClassDoc targetClass) {
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
        List<PropertyDoc> properties = []
        mixinClasses.each { mixin ->
            mixin.classProperties.each { prop ->
                properties << prop.forClass(targetClass)
            }
        }
        extraProperties.each { prop ->
            properties << prop.forClass(targetClass)
        }
        return properties.sort { it.name }
    }

    List<MethodDoc> getExtensionMethods() {
        List<MethodDoc> methods = []
        mixinClasses.each { mixin ->
            mixin.classMethods.each { method ->
                methods << method.forClass(targetClass)
            }
        }
        return methods.sort { it.metaData.overrideSignature }
    }

    List<BlockDoc> getExtensionBlocks() {
        List<BlockDoc> blocks = []
        mixinClasses.each { mixin ->
            mixin.classBlocks.each { block ->
                blocks << block.forClass(targetClass)
            }
        }
        extraBlocks.each { block->
            blocks << block.forClass(targetClass)
        }
        return blocks.sort { it.name }
    }
}

