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

package org.gradle.api.internal.artifacts;

import org.gradle.util.Path;

/**
 * A build that is not the current build. This type exists only to provide an answer to {@link #isCurrentBuild()}, which should not exist.
 */
public class ForeignBuildIdentifier extends DefaultBuildIdentifier {

    public ForeignBuildIdentifier(Path buildPath) {
        super(buildPath);
    }

    @Override
    public boolean isCurrentBuild() {
        nagAboutDeprecatedIsCurrentBuild();
        return false;
    }
}
