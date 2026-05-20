/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model

import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.longlived.PersistentBuildProcessIntegrationTest
import org.gradle.model.internal.inspect.ModelRuleExtractor

@UnsupportedWithConfigurationCache(because = "software model")
class ModelRuleCachingIntegrationTest extends PersistentBuildProcessIntegrationTest {

    def setup() {
        // A stateless rule source (no managed properties): the extractor caches one
        // CachedRuleSource per class and reuses the same extracted instance on every call.
        // It lives in buildSrc so the daemon reuses its class loader, keeping the Class
        // identity (the cache key) stable across builds.
        file("buildSrc/src/main/java/org/gradle/integtest/CachingRuleSource.java") << """
            package org.gradle.integtest;

            import org.gradle.model.RuleSource;

            public class CachingRuleSource extends RuleSource {}
        """
        buildFile << """
            def extractor = project.services.get($ModelRuleExtractor.name)
            def ruleSource = extractor.extract(org.gradle.integtest.CachingRuleSource)
            println "### rule source id: \${System.identityHashCode(ruleSource)}"
        """
    }

    private String getExtractedRuleSourceId() {
        def match = output =~ /### rule source id: (\d+)/
        match[0][1]
    }

    def "rule sources are reused across builds in a persistent process"() {
        when:
        run()
        def firstId = extractedRuleSourceId
        run()
        def secondId = extractedRuleSourceId

        then:
        firstId == secondId
    }
}
