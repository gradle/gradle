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

class IvyRepository {
    final TestFile rootDir

    IvyRepository(TestFile rootDir) {
        this.rootDir = rootDir
    }

    String getPattern() {
        return "[organisation]/[module]/[revision]/[artifact]-[revision].[ext]"
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

    IvyModule(TestFile moduleDir, String organisation, String module, String revision) {
        this.moduleDir = moduleDir
        this.organisation = organisation
        this.module = module
        this.revision = revision
    }

    File getIvyFile() {
        return moduleDir.file("ivy-${revision}.xml")
    }

    File getJarFile() {
        return moduleDir.file("$module-${revision}.jar")
    }

    File publishArtifact() {
        moduleDir.createDir()

        ivyFile << """<?xml version="1.0" encoding="UTF-8"?>
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
</ivy-module>
        """

        jarFile << "add some content so that file size isn't zero"

        return jarFile
    }
}
