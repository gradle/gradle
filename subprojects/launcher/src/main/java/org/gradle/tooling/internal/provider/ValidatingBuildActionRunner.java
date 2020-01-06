/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;

public class ValidatingBuildActionRunner implements BuildActionRunner {
    private final BuildActionRunner delegate;

    public ValidatingBuildActionRunner(BuildActionRunner delegate) {
        this.delegate = delegate;
    }

    @Override
    public Result run(BuildAction action, BuildController buildController) {
        Result result = delegate.run(action, buildController);
        if (!result.hasResult()) {
            throw new UnsupportedOperationException(String.format("Don't know how to run a build action of type %s.", action.getClass().getSimpleName()));
        }
        return result;
    }
}
