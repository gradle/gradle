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

package org.gradle.api.internal.project;

import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Logger;
import org.apache.tools.ant.Task;
import org.slf4j.LoggerFactory;

public class TestAntTask extends Task {
    @Override
    public void execute() {
        LogFactory.getLog("ant-test").info("a jcl log message");
        LoggerFactory.getLogger("ant-test").info("an slf4j log message");
        Logger.getLogger("ant-test").info("a log4j log message");
    }
}
