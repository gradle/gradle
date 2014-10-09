/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData
import spock.lang.Specification

class MavenNewestVersionMatcherSpec extends Specification {
    def matcher = new MavenNewestVersionMatcher()

    def "does not accept SNAPSHOT versions for RELEASE"() {
        expect:
        !matcher.accept("RELEASE", "1-SNAPSHOT")
        !matcher.accept("RELEASE", "1.5-SNAPSHOT")
        !matcher.accept("RELEASE", "1-20141009.112124-11")
        !matcher.accept("RELEASE", "1.5-20141009.112124-11")
    }

    def "accepts SNAPSHOT versions for LATEST"() {
        expect:
        matcher.accept("LATEST", "1-SNAPSHOT")
        matcher.accept("LATEST", "1.5-SNAPSHOT")
        matcher.accept("LATEST", "1-20141009.112124-11")
        matcher.accept("LATEST", "1.5-20141009.112124-11")
    }

    def "does not need metadata"() {
        expect:
        !matcher.needModuleMetadata("LATEST")
        !matcher.needModuleMetadata("RELEASE")
    }

    def "is dynmaic"() {
        expect:
        matcher.isDynamic("LATEST")
        matcher.isDynamic("RELEASE")
    }

    def "accepts metadata-aware accept method"(){
        def metadata = Stub(ModuleComponentResolveMetaData) {
            getComponentId() >> Mock(ModuleComponentIdentifier){
                _ * getVersion() >> "1.0-SNAPSHOT"
            }
        }

        expect:
        matcher.accept("LATEST", metadata)
        !matcher.accept("RELEASE", metadata)
    }
}
