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
package org.gradle.groovy.compile

import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.util.VersionNumber

@TargetVersions(['1.6.9', '1.7.11', '1.8.8', '2.0.3'])
class AntForkingGroovyCompilerIntegrationTest extends GroovyCompilerIntegrationSpec {

    @Override
    String compilerConfiguration() {
        '''
    tasks.withType(GroovyCompile) {
        groovyOptions.useAnt = true
        groovyOptions.fork = true
    }
'''
    }

    @Override
    String getCompilationFailureMessage() {
        return "Forked groovyc returned error code: 1"
    }

    @Override
    String getCompileErrorOutput() {
        if (version == '1.7.11' || versionNumber >= VersionNumber.parse('1.8.0')) {
            return output
        }
        return errorOutput
    }
}
