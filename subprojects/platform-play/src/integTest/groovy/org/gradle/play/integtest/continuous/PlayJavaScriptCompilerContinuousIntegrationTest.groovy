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

package org.gradle.play.integtest.continuous

import org.gradle.play.integtest.fixtures.AbstractPlayCompilerContinuousIntegrationTest

class PlayJavaScriptCompilerContinuousIntegrationTest extends AbstractPlayCompilerContinuousIntegrationTest {
    @Override
    String getCompileTaskName() {
        return "minifyPlayBinaryJavaScript"
    }

    @Override
    String getCompileTaskType() {
        return "JavaScriptMinify"
    }

    @Override
    String getSourceFileName() {
        return "app/assets/foo.js"
    }

    @Override
    String getInitialSourceContent() {
        return "var number = 42;"
    }

    @Override
    String getChangedSourceContent() {
        return "var number = 420;"
    }

    @Override
    String getPlugins() {
        return """
            ${super.getPlugins()}
            id 'play-javascript'
        """
    }
}
