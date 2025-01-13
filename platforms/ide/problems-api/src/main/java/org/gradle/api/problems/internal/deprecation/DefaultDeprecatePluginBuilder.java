package org.gradle.api.problems.internal.deprecation;

import org.gradle.api.problems.deprecation.DeprecatePluginSpec;
import org.gradle.api.problems.internal.InternalProblemBuilder;

public class DefaultDeprecatePluginBuilder extends DefaultCommonDeprecationBuilder<DeprecatePluginSpec> implements DeprecatePluginSpec {
    public DefaultDeprecatePluginBuilder(InternalProblemBuilder builder) {
        super(builder);
    }
}
