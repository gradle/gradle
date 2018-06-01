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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.JDK8_OR_LATER)
class PlayTwirlCompilerContinuousIntegrationTest extends AbstractPlayCompilerContinuousIntegrationTest {
    @Override
    String getCompileTaskName() {
        return "compilePlayBinaryPlayTwirlTemplates"
    }

    @Override
    String getCompileTaskType() {
        return "TwirlCompile"
    }

    @Override
    String getSourceFileName() {
        return "app/views/foo.scala.html"
    }

    @Override
    String getInitialSourceContent() {
        return """
            <html>
            @title(text: String) = @{
              text.split(' ').map(_.capitalize).mkString(" ")
            }
            </html>
        """
    }

    @Override
    String getChangedSourceContent() {
        return """
            <html>
                @title(text: String) = @{
                  text.split(' ').map(_.capitalize).mkString(" ")
                }

                <title>@title("test")</title>
            </html>
        """
    }
}
