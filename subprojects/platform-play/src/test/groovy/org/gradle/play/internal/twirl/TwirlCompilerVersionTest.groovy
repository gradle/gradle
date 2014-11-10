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

package org.gradle.play.internal.twirl

import org.gradle.play.internal.twirl.spec.TwirlCompilerVersion
import spock.lang.Specification

class TwirlCompilerVersionTest extends Specification {

    def "parser detects 1.0.2 version"(){
        expect:
        TwirlCompilerVersion.parse("1.0.2") == TwirlCompilerVersion.V_10X
    }

    def "parser detects to 2.2.x versions"(){
        expect:
        TwirlCompilerVersion.parse("2.2.3") == TwirlCompilerVersion.V_22X
        TwirlCompilerVersion.parse("2.2.4") == TwirlCompilerVersion.V_22X
        TwirlCompilerVersion.parse("2.2.5") == TwirlCompilerVersion.V_22X
    }

}
