/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.util.TestFile
import junit.framework.AssertionFailedError
import java.util.regex.Pattern

class IvyRepository {
    final TestFile rootDir

    IvyRepository(TestFile rootDir) {
        this.rootDir = rootDir
    }

    IvyModule module(String organisation, String module, Object revision = '1.0') {
        def moduleDir = rootDir.file("$organisation/$module/$revision")
        return new IvyModule(moduleDir, organisation, module, revision as String)
    }
}

class IvyModule {
    final TestFile moduleDir
    final String organisation
    final String module
    final String revision
    final List dependencies = []
    int publishCount

    IvyModule(TestFile moduleDir, String organisation, String module, String revision) {
        this.moduleDir = moduleDir
        this.organisation = organisation
        this.module = module
        this.revision = revision
    }

    IvyModule dependsOn(String organisation, String module, String revision) {
        dependencies << [organisation: organisation, module: module, revision: revision]
        return this
    }

    File getIvyFile() {
        return moduleDir.file("ivy-${revision}.xml")
    }

    TestFile getJarFile() {
        return moduleDir.file("$module-${revision}.jar")
    }

    void publishWithChangedContent() {
        publishCount++
        publishArtifact()
    }

    File publishArtifact() {
        moduleDir.createDir()

        ivyFile.text = """<?xml version="1.0" encoding="UTF-8"?>
<ivy-module version="1.0">
	<info organisation="${organisation}"
		module="${module}"
		revision="${revision}"
	/>
	<configurations>
		<conf name="runtime" visibility="public"/>
		<conf name="default" visibility="public" extends="runtime"/>
	</configurations>
	<publications>
		<artifact name="${module}" type="jar" ext="jar" conf="*"/>
	</publications>
	<dependencies>
"""
        dependencies.each { dep ->
            ivyFile << """
        <dependency org="${dep.organisation}" name="${dep.module}" rev="${dep.revision}"/>
"""
        }
        ivyFile << """
    </dependencies>
</ivy-module>
        """

        jarFile << "add some content so that file size isn't zero: $publishCount"

        return jarFile
    }

    /**
     * Asserts that exactly the given artifacts have been published.
     */
    void assertArtifactsPublished(String... names) {
        names.each {
            assert moduleDir.list() as Set == names as Set
        }
    }

    IvyDescriptor getIvy() {
        return new IvyDescriptor(ivyFile)
    }
}

class IvyDescriptor {
    final Map<String, IvyConfiguration> configurations = [:]

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
    }
}

class IvyConfiguration {
    final dependencies = []

    void addDependency(String org, String module, String revision) {
        dependencies << [org: org, module: module, revision: revision]
    }

    void assertDependsOn(String org, String module, String revision) {
        def dep = [org: org, module: module, revision: revision]
        if (!dependencies.find { it == dep}) {
            throw new AssertionFailedError("Could not find expected dependency $dep. Actual: $dependencies")
        }
    }
}
