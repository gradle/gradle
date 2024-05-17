/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.configurationcache.fixtures.DefaultExcludesFixture

class ConfigurationCacheFileSystemDefaultExcludesTest extends AbstractConfigurationCacheIntegrationTest {

    def "default excludes defined in #spec are restoring from configuration cache"() {
        given:
        spec.setup(this)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun spec.copyTask

        then:
        configurationCache.assertStateStored()
        !spec.excludedFilesCopies.any { it.exists() }
        spec.includedFilesCopies.every { it.exists() }

        when:
        configurationCacheRun spec.copyTask

        then:
        configurationCache.assertStateLoaded()
        result.assertTaskSkipped(":$spec.copyTask")

        when:
        spec.mutateExcludedFiles()

        and:
        configurationCacheRun spec.copyTask

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksSkipped(":$spec.copyTask")

        where:
        spec << DefaultExcludesFixture.specs()
    }

    def "default excludes defined in build#scriptLanguage are ignoring for snapshotting"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        def spec = new DefaultExcludesFixture.Spec(
            Collections.singletonList(new DefaultExcludesFixture.RootBuildLocation()),
            scriptLanguage
        )
        spec.setup(this)

        def excludedByBuildScriptFileName = "excluded-by-build-script.txt"
        def excludedByBuildScriptFile = file("input/${excludedByBuildScriptFileName}")
        def excludedByBuildScriptFileCopy = file("build/output/${excludedByBuildScriptFileName}")
        excludedByBuildScriptFile.text = "input"

        if (scriptLanguage == DefaultExcludesFixture.ScriptLanguage.KOTLIN) {
            buildKotlinFile << DefaultExcludesFixture.DefaultExcludesLocation.addDefaultExclude(excludedByBuildScriptFileName)
        } else {
            buildFile << DefaultExcludesFixture.DefaultExcludesLocation.addDefaultExclude(excludedByBuildScriptFileName)
        }

        when:
        configurationCacheRun spec.copyTask

        then:
        configurationCache.assertStateStored()
        !spec.excludedFilesCopies.any { it.exists() }
        spec.includedFilesCopies.every { it.exists() }
        excludedByBuildScriptFileCopy.exists()

        when:
        excludedByBuildScriptFile.text = "Changed!"
        configurationCacheRun spec.copyTask

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksExecuted(":$spec.copyTask")
        excludedByBuildScriptFileCopy.exists()

        where:
        scriptLanguage << DefaultExcludesFixture.ScriptLanguage.values().toList()
    }
}
