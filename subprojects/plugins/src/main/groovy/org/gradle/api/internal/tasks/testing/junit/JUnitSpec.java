package org.gradle.api.internal.tasks.testing.junit;

import org.gradle.api.tasks.testing.junit.JUnitOptions;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class JUnitSpec implements Serializable {

    private final Set<String> includeCategories;
    private final Set<String> excludeCategories;

    public JUnitSpec(final JUnitOptions options){
        this.includeCategories = options.getIncludeGroups();
        this.excludeCategories = options.getExcludeGroups();
    }

    public Set<String> getIncludeCategories() {
        return includeCategories;
    }

    public Set<String> getExcludeCategories() {
        return excludeCategories;
    }
}
