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
package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A provider of dependency resolution metadata for local components produced by any build in the build tree.
 *
 * <p>Currently, the metadata for a component is different based on whether it is consumed from the
 * producing build or from another build. Depending on the consumer, either {@link #getLocalComponent}
 * or {@link #getForeignComponent} should be called. Eventually, once {@link BuildIdentifier#isCurrentBuild()}
 * is removed, we can eliminate this distinction.</p>
 *
 * <p>In general, you should be using {@link LocalComponentRegistry} instead of this type, as it is scoped
 * to a build and will call the appropriate method on this provider.</p>
 */
@ThreadSafe
public interface BuildTreeLocalComponentProvider {
    LocalComponentGraphResolveState getLocalComponent(ProjectComponentIdentifier projectIdentifier);

    LocalComponentGraphResolveState getForeignComponent(ProjectComponentIdentifier projectIdentifier);
}
