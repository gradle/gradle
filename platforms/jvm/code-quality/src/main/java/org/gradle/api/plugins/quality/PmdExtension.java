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
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.resources.TextResource;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration options for the PMD plugin.
 *
 * @see PmdPlugin
 */
public abstract class PmdExtension extends CodeQualityExtension {

    private final Project project;

    private TargetJdk targetJdk;
    private TextResource ruleSetConfig;
    private ConfigurableFileCollection ruleSetFiles;
    private boolean consoleOutput;
    private final ListProperty<String> ruleSets;
    private final Property<Integer> rulesMinimumPriority;
    private final Property<Integer> maxFailures;
    private final Property<Boolean> incrementalAnalysis;
    private final Property<Integer> threads;

    public PmdExtension(Project project) {
        this.project = project;
        this.rulesMinimumPriority = project.getObjects().property(Integer.class);
        this.incrementalAnalysis = project.getObjects().property(Boolean.class);
        this.maxFailures = project.getObjects().property(Integer.class);
        this.threads = project.getObjects().property(Integer.class);
        this.ruleSets = project.getObjects().listProperty(String.class);
    }

    /**
     * The built-in rule sets to be used. See the <a href="https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_rules_java.html">official list</a> of built-in rule sets.
     *
     * If not configured explicitly, the returned conventional value is "category/java/errorprone.xml", unless {@link #getRuleSetConfig()} returns.
     * a non-null value or the return value of {@link #getRuleSetFiles()} is non-empty, in which case the conventional value is an empty list
     *
     * <pre>
     *     ruleSets = ["category/java/errorprone.xml", "category/java/bestpractices.xml"]
     * </pre>
     */
    @ToBeReplacedByLazyProperty
    public List<String> getRuleSets() {
        return ruleSets.get();
    }

    /**
     * The built-in rule sets to be used. See the <a href="https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_rules_java.html">official list</a> of built-in rule sets.
     *
     * <pre>
     *     ruleSets = ["category/java/errorprone.xml", "category/java/bestpractices.xml"]
     * </pre>
     */
    public void setRuleSets(List<String> ruleSets) {
        this.ruleSets.set(ruleSets);
    }

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
        this.ruleSets.addAll(Arrays.asList(ruleSets));
    }

    /**
     * The target jdk to use with pmd, 1.3, 1.4, 1.5, 1.6, 1.7 or jsp
     */
    @ToBeReplacedByLazyProperty
    public TargetJdk getTargetJdk() {
        return targetJdk;
    }

    /**
     * Sets the target jdk used with pmd.
     *
     * @param targetJdk The target jdk
     * @since 4.0
     */
    public void setTargetJdk(TargetJdk targetJdk) {
        this.targetJdk = targetJdk;
    }

    /**
     * The maximum number of failures to allow before stopping the build.
     *
     * If <pre>ignoreFailures</pre> is set, this is ignored and no limit is enforced.
     *
     * @since 6.4
     */
    public Property<Integer> getMaxFailures() {
        return maxFailures;
    }

    /**
     * Sets the target jdk used with pmd.
     *
     * @param value The value for the target jdk as defined by {@link TargetJdk#toVersion(Object)}
     */
    public void setTargetJdk(Object value) {
        targetJdk = TargetJdk.toVersion(value);
    }

    /**
     * The rule priority threshold; violations for rules with a lower priority will not be reported. Default value is 5, which means that all violations will be reported.
     *
     * This is equivalent to PMD's Ant task minimumPriority property.
     *
     * See the official documentation for the <a href="https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_userdocs_configuring_rules.html">list of priorities</a>.
     *
     * <pre>
     *     rulesMinimumPriority = 3
     * </pre>
     *
     * @since 6.8
     */
    public Property<Integer> getRulesMinimumPriority() {
        return rulesMinimumPriority;
    }

    /**
     * The custom rule set to be used (if any). Replaces {@code ruleSetFiles}, except that it does not currently support multiple rule sets.
     *
     * See the <a href="https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_userdocs_making_rulesets.html">official documentation</a> for how to author a rule set.
     *
     * <pre>
     *     ruleSetConfig = resources.text.fromFile("config/pmd/myRuleSet.xml")
     * </pre>
     *
     * @since 2.2
     */
    @Nullable
    @ToBeReplacedByLazyProperty
    public TextResource getRuleSetConfig() {
        return ruleSetConfig;
    }

    /**
     * The custom rule set to be used (if any). Replaces {@code ruleSetFiles}, except that it does not currently support multiple rule sets.
     *
     * See the <a href="https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_userdocs_making_rulesets.html">official documentation</a> for how to author a rule set.
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
     * The custom rule set files to be used. See the <a href="https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_userdocs_making_rulesets.html">official documentation</a> for how to author a rule set file.
     *
     * <pre>
     *     ruleSetFiles = files("config/pmd/myRuleSet.xml")
     * </pre>
     */
    @ToBeReplacedByLazyProperty
    public FileCollection getRuleSetFiles() {
        return ruleSetFiles;
    }

    /**
     * The custom rule set files to be used. See the <a href="https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_userdocs_making_rulesets.html">official documentation</a> for how to author a rule set file.
     * This adds to the default rule sets defined by {@link #getRuleSets()}.
     *
     * <pre>
     *     ruleSetFiles = files("config/pmd/myRuleSets.xml")
     * </pre>
     */
    public void setRuleSetFiles(FileCollection ruleSetFiles) {
        this.ruleSetFiles = project.getObjects().fileCollection().from(ruleSetFiles);
    }

    /**
     * Convenience method for adding rule set files.
     *
     * <pre>
     *     ruleSetFiles "config/pmd/myRuleSet.xml"
     * </pre>
     *
     * @param ruleSetFiles the rule set files to be added
     */
    public void ruleSetFiles(Object... ruleSetFiles) {
        this.ruleSetFiles.from(ruleSetFiles);
    }

    /**
     * Whether or not to write PMD results to {@code System.out}.
     */
    @ToBeReplacedByLazyProperty
    public boolean isConsoleOutput() {
        return consoleOutput;
    }

    /**
     * Whether or not to write PMD results to {@code System.out}.
     */
    public void setConsoleOutput(boolean consoleOutput) {
        this.consoleOutput = consoleOutput;
    }

    /**
     * Controls whether to use incremental analysis or not.
     *
     * This is only supported for PMD 6.0.0 or better. See <a href="https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_userdocs_incremental_analysis.html"></a> for more details.
     *
     * @since 5.6
     */
    public Property<Boolean> getIncrementalAnalysis() {
        return incrementalAnalysis;
    }

    /**
     * The number of threads used by PMD.
     *
     * @since 7.5
     */
    public Property<Integer> getThreads() {
        return threads;
    }

    void ruleSetsConvention(Provider<List<String>> provider) {
        ruleSets.convention(provider);
    }
}
