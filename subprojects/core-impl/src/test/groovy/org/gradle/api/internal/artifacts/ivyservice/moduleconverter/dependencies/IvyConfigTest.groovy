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

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies

import spock.lang.Specification

/**
 * @author: Szczepan Faber, created at: 9/29/11
 */
public class IvyConfigTest extends Specification {

    def "works fine"() {
        expect:
        //TODO SF add test
        1 == 1
//        new ModuleId(ExactPatternMatcher.ANY_EXPRESSION, ExactPatternMatcher.ANY_EXPRESSION));
//        assertThat(conflictManager, instanceOf(LatestConflictManager.class));
//        assertThat(((LatestConflictManager) conflictManager).getSettings(), equalTo(ivySettingsDummy));
    }
}
