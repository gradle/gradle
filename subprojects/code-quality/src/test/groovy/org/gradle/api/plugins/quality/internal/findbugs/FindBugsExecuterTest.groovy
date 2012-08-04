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

package org.gradle.api.plugins.quality.internal.findbugs

import edu.umd.cs.findbugs.IFindBugsEngine
import spock.lang.Specification

class FindBugsExecuterTest extends Specification {

    def FindBugsExecuter findBugsExecuter = new FindBugsExecuter();

    def "FindBugsResult contains bugCount from FindBugsEngine"() {
        setup:
        IFindBugsEngine findbugs = Mock(IFindBugsEngine)
        findbugs.bugCount >> 1;
        when:
        def result = findBugsExecuter.createFindbugsResult(findbugs)
        then:
        result.bugCount == 1
    }

    def "FindBugsResult contains errorCount from FindBugsEngine"() {
        setup:
        IFindBugsEngine findbugs = Mock(IFindBugsEngine)
        findbugs.errorCount >> 1;
        when:
        def result = findBugsExecuter.createFindbugsResult(findbugs)
        then:
        result.errorCount == 1
    }

    def "FindBugsResult contains missingClassCount from FindBugsEngine"() {
        setup:
        IFindBugsEngine findbugs = Mock(IFindBugsEngine)
        findbugs.missingClassCount >> 1;
        when:
        def result = findBugsExecuter.createFindbugsResult(findbugs)
        then:
        result.missingClassCount == 1
    }
}
