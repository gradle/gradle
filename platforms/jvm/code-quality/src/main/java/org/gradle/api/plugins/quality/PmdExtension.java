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
package org.gradle.api.plugins.quality;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.resources.TextResource;
import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * Configuration options for the PMD plugin.
 *
 * @see PmdPlugin
 */
public abstract class PmdExtension extends CodeQualityExtension {

    private TextResource ruleSetConfig;

    public PmdExtension(Project project) {
        getConsoleOutput().convention(false);
    }

    ListProperty<String> getRuleSetsProperty() {
        return getRuleSets();
    }

    /**
     * The built-in rule sets to be used. See the <a href="https://docs.pmd-code.org/pmd-doc-7.13.0/pmd_rules_java.html">official list</a> of built-in rule sets.
     *
     * If not configured explicitly, the returned conventional value is "category/java/errorprone.xml", unless {@link #getRuleSetConfig()} returns
     * a non-null value or the return value of {@link #getRuleSetFiles()} is non-empty, in which case the conventional value is an empty list.
     *
     * <pre>
     *     ruleSets = ["category/java/errorprone.xml", "category/java/bestpractices.xml"]
     * </pre>
     */
    @ReplacesEagerProperty
    public abstract ListProperty<String> getRuleSets();

    /**
     * Convenience method for adding rule sets.
     *
     * <pre>
     *     ruleSets "category/java/errorprone.xml", "category/java/bestpractices.xml"
     * </pre>
     *
     * @param ruleSets the rule sets to be added
     */
    public void ruleSets(String... ruleSets) {
        this.getRuleSetsProperty().addAll(Arrays.asList(ruleSets));
    }

    /**
     * The target jdk to use with pmd, 1.3, 1.4, 1.5, 1.6, 1.7 or jsp
     */
    @ReplacesEagerProperty(adapter = TargetJdkAdapter.class)
    public abstract Property<TargetJdk> getTargetJdk();

    /**
     * The maximum number of failures to allow before stopping the build.
     *
     * If {@code ignoreFailures} is set, this is ignored and no limit is enforced.
     *
     * @since 6.4
     */
    public abstract Property<Integer> getMaxFailures();

    /**
     * The rule priority threshold; violations for rules with a lower priority will not be reported. Default value is 5, which means that all violations will be reported.
     *
     * This is equivalent to PMD's Ant task minimumPriority property.
     *
     * See the official documentation for the <a href="https://docs.pmd-code.org/pmd-doc-7.13.0/pmd_userdocs_configuring_rules.html">list of priorities</a>.
     *
     * <pre>
     *     rulesMinimumPriority = 3
     * </pre>
     *
     * @since 6.8
     */
    public abstract Property<Integer> getRulesMinimumPriority();

    /**
     * The custom rule set to be used (if any). Replaces {@code ruleSetFiles}, except that it does not currently support multiple rule sets.
     *
     * See the <a href="https://docs.pmd-code.org/pmd-doc-7.13.0/pmd_userdocs_making_rulesets.html">official documentation</a> for how to author a rule set.
     *
     * <pre>
     *     ruleSetConfig = resources.text.fromFile("config/pmd/myRuleSet.xml")
     * </pre>
     *
     * @since 2.2
     */
    @Nullable
    @NotToBeReplacedByLazyProperty(because = "TextResource has no lazy replacement")
    public TextResource getRuleSetConfig() {
        return ruleSetConfig;
    }

    /**
     * The custom rule set to be used (if any). Replaces {@code ruleSetFiles}, except that it does not currently support multiple rule sets.
     *
     * See the <a href="https://docs.pmd-code.org/pmd-doc-7.13.0/pmd_userdocs_making_rulesets.html">official documentation</a> for how to author a rule set.
     *
     * <pre>
     *     ruleSetConfig = resources.text.fromFile("config/pmd/myRuleSet.xml")
     * </pre>
     *
     * @since 2.2
     */
    public void setRuleSetConfig(@Nullable TextResource ruleSetConfig) {
        this.ruleSetConfig = ruleSetConfig;
    }

    /**
     * The custom rule set files to be used. See the <a href="https://docs.pmd-code.org/pmd-doc-7.13.0/pmd_userdocs_making_rulesets.html">official documentation</a> for how to author a rule set file.
     *
     * <pre>
     *     ruleSetFiles = files("config/pmd/myRuleSet.xml")
     * </pre>
     */
    @ReplacesEagerProperty
    public abstract ConfigurableFileCollection getRuleSetFiles();

    /**
     * Convenience method for adding rule set files.
     *
     * <pre>
     *     ruleSetFiles "config/pmd/myRuleSet.xml"
     * </pre>
     *
     * @param ruleSetFiles the rule set files to be added
     */
    public void ruleSetFiles(@Nullable Object... ruleSetFiles) {
        getRuleSetFiles().from(ruleSetFiles);
    }

    /**
     * Whether or not to write PMD results to {@code System.out}.
     */
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getConsoleOutput();

    @Deprecated
    public Property<Boolean> getIsConsoleOutput() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsConsoleOutput()", "getConsoleOutput()");
        return getConsoleOutput();
    }

    /**
     * Controls whether to use incremental analysis or not.
     *
     * This is only supported for PMD 6.0.0 or better. See <a href="https://docs.pmd-code.org/pmd-doc-7.13.0/pmd_userdocs_incremental_analysis.html">official documentation</a> for more details.
     *
     * @since 5.6
     */
    public abstract Property<Boolean> getIncrementalAnalysis();

    /**
     * The number of threads used by PMD.
     *
     * @since 7.5
     */
    public abstract Property<Integer> getThreads();

    static class TargetJdkAdapter {
        @Nullable
        @BytecodeUpgrade
        static TargetJdk getTargetJdk(PmdExtension extension) {
            return extension.getTargetJdk().getOrNull();
        }

        @BytecodeUpgrade
        static void setTargetJdk(PmdExtension extension, TargetJdk targetJdk) {
            extension.getTargetJdk().set(targetJdk);
        }

        @BytecodeUpgrade
        static void setTargetJdk(PmdExtension extension, Object value) {
            extension.getTargetJdk().set(TargetJdk.toVersion(value));
        }
    }
}
