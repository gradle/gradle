/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.r32;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;

abstract class BrokenBuildAction implements BuildAction<String> {
    public static final String BUILD_ACTION_EXCEPTION_MESSAGE = "Something went wrong when executing the build action";

    public String execute(BuildController controller) {
        throwException();
        return "";
    }

    abstract void throwException();
}
