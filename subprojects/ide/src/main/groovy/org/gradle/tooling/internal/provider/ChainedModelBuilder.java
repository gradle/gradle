package org.gradle.tooling.internal.provider;

import org.gradle.api.internal.GradleInternal;

/**
 * @author: Szczepan Faber, created at: 6/11/11
 */
public interface ChainedModelBuilder {
    Object buildAll(GradleInternal gradle, Object currentModel);
}