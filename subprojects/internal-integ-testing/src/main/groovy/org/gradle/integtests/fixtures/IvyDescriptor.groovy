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

package org.gradle.integtests.fixtures

import java.util.regex.Pattern

class IvyDescriptor {
    final Map<String, IvyConfiguration> configurations = [:]
    Map<String, IvyArtifact> artifacts = [:]

    IvyDescriptor(File ivyFile) {
        def ivy = new XmlParser().parse(ivyFile)
        ivy.dependencies.dependency.each { dep ->
            def configName = dep.@conf ?: "default"
            def matcher = Pattern.compile("(\\w+)->\\w+").matcher(configName)
            if (matcher.matches()) {
                configName = matcher.group(1)
            }
            def config = configurations[configName]
            if (!config) {
                config = new IvyConfiguration()
                configurations[configName] = config
            }
            config.addDependency(dep.@org, dep.@name, dep.@rev)
        }
        ivy.publications.artifact.each { artifact ->
            def ivyArtifact = new IvyArtifact(name: artifact.@name, type: artifact.@type, ext: artifact.@ext, conf: artifact.@conf.split(",") as List)
            artifacts.put(ivyArtifact.name, ivyArtifact)
        }
    }

    IvyArtifact expectArtifact(String name) {
        assert artifacts.containsKey(name)
        artifacts[name]
    }
}
