/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.fixtures.console

import org.gradle.integtests.fixtures.AbstractMultiTestRunner
import org.gradle.integtests.fixtures.executer.ConsoleAttachment

class ConsoleAttachmentTestRunner extends AbstractMultiTestRunner {
    ConsoleAttachmentTestRunner(Class<? extends AbstractConsoleGroupedTaskFunctionalTest> target) {
        super(target)
    }

    @Override
    protected void createExecutions() {
        ConsoleAttachment.values().each { ConsoleAttachment attachment ->
            add(new ConsoleAttachmentExecution(attachment))
        }
    }

    private static class ConsoleAttachmentExecution extends AbstractMultiTestRunner.Execution {
        private final ConsoleAttachment attachment

        ConsoleAttachmentExecution(ConsoleAttachment attachment) {
            this.attachment = attachment
        }

        @Override
        protected String getDisplayName() {
            return attachment.description
        }

        @Override
        protected void before() {
            AbstractConsoleGroupedTaskFunctionalTest.consoleAttachment = attachment
        }
    }
}
