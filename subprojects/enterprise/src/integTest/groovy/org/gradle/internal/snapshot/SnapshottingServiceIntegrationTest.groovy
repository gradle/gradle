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

package org.gradle.internal.snapshot

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.fingerprint.IgnoredPathInputNormalizer
import org.gradle.test.fixtures.plugin.PluginBuilder

class SnapshottingServiceIntegrationTest extends AbstractIntegrationSpec {

    def "can inject service into plugin"() {
        def pluginBuilder = new PluginBuilder(file("buildSrc"))
        pluginBuilder.addPlugin("""
            $SnapshottingService.name service = project.getServices(${SnapshottingService.name}.class);
            $Snapshot.name hash = service.snapshotFor(project.file("input.txt").toPath(), ${IgnoredPathInputNormalizer.name}.class);
            System.out.println("Hash: " + hash.getHashValue());
        """
        )
        buildFile << """
        """
    }
}
