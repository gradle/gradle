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

package org.gradle.api.internal.artifacts.result;

import org.gradle.api.artifacts.result.ResolvedDependencyResult;

/**
 * Created: 17/08/2012
 *
 * @author Szczepan Faber
 */
public class ResolvedDependencyResultPrinter {

    public static String print(ResolvedDependencyResult result) {
        if (!result.getRequested().getAsSpec().isSatisfiedBy(result.getSelected().getId())) {
            return requested(result) + " -> " + result.getSelected().getId().getVersion();
        } else {
            return requested(result);
        }
    }

    private static String requested(ResolvedDependencyResult result) {
        return result.getRequested().getGroup() + ":" + result.getRequested().getName() + ":" + result.getRequested().getVersion();
    }
}
