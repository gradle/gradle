/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.buildinit.plugins.internal.action;

import org.gradle.configuration.project.BuiltInCommand;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

// TODO: Consider moving the InitBuiltInCommand (and help) to core, as they are not Software Platform-specific
@ServiceScope(Scope.Global.class)
public class InitBuiltInCommand implements BuiltInCommand {
    public static final String NAME = "init";

    @Override
    @Nonnull
    public String getDisplayName() {
        return NAME;
    }

    @Override
    public List<String> asDefaultTask() {
        return Collections.emptyList();
    }

    @Override
    public boolean commandLineMatches(List<String> taskNames) {
        return !taskNames.isEmpty() && taskNames.stream().anyMatch(taskName -> taskName.equals(NAME));
    }

    @Override
    public boolean requireEmptyBuildDefinition() {
        return true;
    }

    @Override
    public boolean isExclusive() {
        return true;
    }
}
