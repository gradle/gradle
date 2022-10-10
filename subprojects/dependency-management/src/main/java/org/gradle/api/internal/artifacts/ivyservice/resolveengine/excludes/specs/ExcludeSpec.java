/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.ExcludeFactory;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Set;

public interface ExcludeSpec {
    /**
     * Determines if this exclude rule excludes the supplied module.
     */
    boolean excludes(ModuleIdentifier module);

    /**
     * Determines if this exclude rule excludes the supplied artifact, for the specified module.
     */
    boolean excludesArtifact(ModuleIdentifier module, IvyArtifactName artifactName);

    /**
     * Tells if this rule may exclude some artifacts. This is used to optimize artifact resolution.
     */
    boolean mayExcludeArtifacts();

    /**
     * This method <STRONG>MUST</STRONG> be overloaded in <strong>EVERY</strong></strong>> extension of this
     * interface - it <strong>CAN NOT BE IMPLEMENTED HERE</strong> with a {@code default} implementation,
     * because it needs to be called within a more specific interface type
     * in order for the simulation of double-dispatch within this call heirarchy to work.
     *
     * All implementations should be identical to the following:
     * <pre>
     *     return other.intersect(this, factory);
     * </pre>
     *
     * @param other the other {@link ExcludeSpec} to intersect with this one
     * @return the result of the intersection
     */
    ExcludeSpec beginIntersect(ExcludeSpec other, ExcludeFactory factory);

    /**
     * Since {@link ExcludeAnyOf} contains the logic to handle intersection with any other {@link ExcludeSpec},
     * we'll always want to call to {@link ExcludeAnyOf#intersect(ExcludeSpec, ExcludeFactory)} to handle this case.
     *
     * @param other the other {@link ExcludeSpec} to intersect with this one
     * @return the result of the intersection
     */
    default ExcludeSpec intersect(ExcludeAnyOf other, ExcludeFactory factory) {
        return other.intersect(this, factory);
    }

    // region default implementations of intersection
    /*
     * The default implementation of any intersection is to return {@code null} to indicate that the intersection
     * is not supported.  This is the case for all {@link ExcludeSpec} implementations that are not {@link ExcludeAnyOf},
     * subtypes will have to {@code @Override} whichever of these applies to them with the logic to figure out
     * each case.
     */
    default ExcludeSpec intersect(ArtifactExclude other, ExcludeFactory factory) {
        return null;
    }

    default ExcludeSpec intersect(ExcludeAllOf other, ExcludeFactory factory) {
        return null;
    }

    default ExcludeSpec intersect(ExcludeEverything other, ExcludeFactory factory) {
        return null;
    }

    default ExcludeSpec intersect(ExcludeNothing other, ExcludeFactory factory) {
        return null;
    }

    default ExcludeSpec intersect(GroupExclude other, ExcludeFactory factory) {
        return null;
    }

    default ExcludeSpec intersect(GroupSetExclude other, ExcludeFactory factory) {
        return null;
    }

    default ExcludeSpec intersect(ModuleExclude other, ExcludeFactory factory) {
        return null;
    }

    default ExcludeSpec intersect(ModuleIdExclude other, ExcludeFactory factory) {
        return null;
    }

    default ExcludeSpec intersect(ModuleIdSetExclude other, ExcludeFactory factory) {
        return null;
    }

    default ExcludeSpec intersect(ModuleSetExclude other, ExcludeFactory factory) {
        return null;
    }

    default ExcludeSpec intersect(ExcludeSpec other, ExcludeFactory factory) {
        return null;
    }
    // endregion default implementations of intersection
}

