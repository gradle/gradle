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

package org.gradle.integtests.resolve

trait PluginDslSupport {
    void withPlugin(String id) {
        withPlugins([(id): null])
    }

    void withPluginAlias(String alias) {
        withPlugins([:], [(alias): null])
    }

    void withPluginAliases(List<String> aliases = []) {
        withPlugins([:], aliases.collectEntries { [it, null] })
    }

    void withPluginsBlockContents(String block) {
        def text = buildFile.text
        int idx = text.indexOf('allprojects')
        text = """${text.substring(0, idx)}
            plugins {
                $block
            }

${text.substring(idx)}
        """
        buildFile.text = text
    }

    void withPlugins(Map<String, String> plugins, Map<String, String> aliases = [:]) {
        def text = buildFile.text
        int idx = text.indexOf('allprojects')
        text = """${text.substring(0, idx)}
            plugins {
                ${plugins.collect { String v = it.value?" version \"${it.value}\"":""; "id '$it.key'$v" }.join('\n')}
                ${aliases.collect { String v = it.value?" version '${it.value}'":""; "alias($it.key)$v" }.join('\n')}
            }

${text.substring(idx)}
        """
        buildFile.text = text
    }
}
