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
package org.gradle.integtests.fixtures;

abstract public class OutputScrapingGradleExecuter extends AbstractGradleExecuter {

    protected class GradleOutput {
        private final String output;
        private final String error;

        public GradleOutput(Object output, Object error) {
            this.output = output.toString();
            this.error = error.toString();
        }

        public String getOutput() {
            return output;
        }

        public String getError() {
            return error;
        }
    }

    @Override
    protected ExecutionResult doRun() {
        GradleOutput output = doRun(false);
        return new OutputScrapingExecutionResult(output.getOutput(), output.getError());
    }

    @Override
    protected ExecutionFailure doRunWithFailure() {
        GradleOutput output = doRun(true);
        return new OutputScrapingExecutionFailure(output.getOutput(), output.getError());
    }

    abstract protected GradleOutput doRun(boolean expectFailure);
}