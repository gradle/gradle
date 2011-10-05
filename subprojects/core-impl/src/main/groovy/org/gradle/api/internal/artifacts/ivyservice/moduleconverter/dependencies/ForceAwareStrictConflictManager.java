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

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.plugins.conflict.AbstractConflictManager;
import org.apache.ivy.plugins.conflict.StrictConflictException;
import org.apache.ivy.plugins.version.VersionMatcher;
import org.gradle.api.internal.artifacts.configurations.conflicts.DependencySelector;

import java.util.Collection;
import java.util.Collections;

/**
 * The vanilla StrictConflictManager ignores 'forced' dependencies hence this implementation.
 * Basically, when the build fails due to version conflict you are able to work around it
 * by forcing certain version of conflicting dependency (using our current api, e.g. force = true).
 *
 * <p>
 * by Szczepan Faber, created at: 10/3/11
 */
public class ForceAwareStrictConflictManager extends AbstractConflictManager {

    private final DependencySelector dependencySelector;

    public ForceAwareStrictConflictManager(DependencySelector dependencySelector) {
        this.dependencySelector = dependencySelector;
    }

    public Collection resolveConflicts(IvyNode parent, Collection conflicts) {
        VersionMatcher versionMatcher = getSettings().getVersionMatcher();

        //SF: inspired on Ivy's StrictConflictManager and LatestConflictManager
        if (conflicts.size() < 2) {
            return conflicts; //no conflicts
        }

        IvyNode lastNode = null;
        for (Object conflict : conflicts) {
            IvyNode node = (IvyNode) conflict;

            if (versionMatcher.isDynamic(node.getResolvedId())) {
                // dynamic revision, not enough information to resolve conflict
                // SF: that's the way StrictConflictManager does it
                return null;
            }

            DependencyDescriptor dd = node.getDependencyDescriptor(parent);
            if (dd != null && dd.isForce()) {
                return Collections.singleton(node);
            }

            if (lastNode != null && !lastNode.equals(node)) {
                IvyNode chosenOne = dependencySelector.maybeSelect(lastNode, node);
                if (chosenOne != null) {
                    return Collections.singleton(chosenOne);
                }
                throw new StrictConflictException(lastNode, node);
            }
            lastNode = node;
        }

        // SF: in theory, we should never reach this return statement
        // but that's how ivy's StrictConflictManager works at the moment
        // so I left it in case I'm not seeing the big picture
        return Collections.singleton(lastNode);
    }
}
