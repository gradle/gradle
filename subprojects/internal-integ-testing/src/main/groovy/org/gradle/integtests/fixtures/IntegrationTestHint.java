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

public class IntegrationTestHint extends RuntimeException {

    public IntegrationTestHint(Throwable cause) {
        super("****\n"
+"This test is one of the integration tests that requires specific tasks to be ran first.\n"
+"Please run: gradle binZip intTestImage publishGradleDistributionPublicationToLocalRepository\n"
+"If the problem persists after running tasks then it probably means it's a genuine test failure.\n"
+"If the task list above is out-of-date please update it.\n"
+"****\n", cause);
    }
}
