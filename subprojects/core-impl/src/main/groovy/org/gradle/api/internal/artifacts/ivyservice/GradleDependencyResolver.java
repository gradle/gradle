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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;

import java.text.ParseException;

public interface GradleDependencyResolver extends ArtifactToFileResolver {
    /**
     * Resolve a module by id, getting its module descriptor and resolving the revision if it's a
     * latest one (i.e. a revision uniquely identifying the revision of a module in the current
     * environment - If this revision is not able to identify uniquelely the revision of the module
     * outside of the current environment, then the resolved revision must begin by ##)
     *
     * @throws java.text.ParseException
     */
    ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data)
            throws ParseException;

    /**
     * Locates the given artifact and returns its location if it can be located by this resolver and
     * if it actually exists, or <code>null</code> in other cases.
     *
     * @param artifact
     *            the artifact which should be located
     * @return the artifact location, or <code>null</code> if it can't be located by this resolver
     *         or doesn't exist.
     */
    ArtifactOrigin locate(Artifact artifact);


}
