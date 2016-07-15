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

import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.resources.TextResource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration options for the PMD plugin.
 *
 * @see PmdPlugin
 */
public class PmdExtension extends CodeQualityExtension {

    private final Project project;

    private List<String> ruleSets;
    private TargetJdk targetJdk;
    private int rulePriority = 5;
    private TextResource ruleSetConfig;
    private FileCollection ruleSetFiles;
    private boolean consoleOutput;

    public PmdExtension(Project project) {
        this.project = project;
    }

    /**
     * The built-in rule sets to be used. See the <a href="http://pmd.sourceforge.net/rules/index.html">official list</a> of built-in rule sets.
     *
     * Example: ruleSets = ["basic", "braces"]
     */
    public List<String> getRuleSets() {
        return ruleSets;
    }

    public void setRuleSets(List<String> ruleSets) {
        this.ruleSets = ruleSets;
    }

    /**
     * Convenience method for adding rule sets.
     *
     * Example: ruleSets "basic", "braces"
     *
     * @param ruleSets the rule sets to be added
     */
    public void ruleSets(String... ruleSets) {
        this.ruleSets.addAll(Arrays.asList(ruleSets));
    }

    /**
     * The target jdk to use with pmd, 1.3, 1.4, 1.5, 1.6, 1.7 or jsp
     */
    public TargetJdk getTargetJdk() {
        return targetJdk;
    }

    /**
     * Sets the target jdk used with pmd.
     *
     * @value The value for the target jdk as defined by {@link TargetJdk#toVersion(Object)}
     */
    public void setTargetJdk(Object value) {
        targetJdk = TargetJdk.toVersion(value);
    }

    /**
     * The rule priority threshold; violations for rules with a lower priority will not be reported. Default value is 5, which means that all violations will be reported.
     *
     * This is equivalent to PMD's Ant task minimumPriority property.
     *
     * See the official documentation for the <a href="http://pmd.sourceforge.net/rule-guidelines.html">list of priorities</a>.
     *
     * Example: rulePriority = 3
     */
    @Incubating
    public int getRulePriority() {
        return rulePriority;
    }

    /**
     * Sets the rule priority threshold.
     */
    @Incubating
    public void setRulePriority(int intValue) {
        Pmd.validate(intValue);
        rulePriority = intValue;
    }

    /**
     * The custom rule set to be used (if any). Replaces {@code ruleSetFiles}, except that it does not currently support multiple rule sets.
     *
     * See the <a href="http://pmd.sourceforge.net/howtomakearuleset.html">official documentation</a> for how to author a rule set.
     *
     * Example: ruleSetConfig = resources.text.fromFile("config/pmd/myRuleSet.xml")
     *
     * @since 2.2
     */
    @Incubating
    public TextResource getRuleSetConfig() {
        return ruleSetConfig;
    }

    @Incubating
    public void setRuleSetConfig(TextResource ruleSetConfig) {
        this.ruleSetConfig = ruleSetConfig;
    }

    /**
     * The custom rule set files to be used. See the <a href="http://pmd.sourceforge.net/howtomakearuleset.html">official documentation</a> for how to author a rule set file.
     *
     * Example: ruleSetFiles = files("config/pmd/myRuleSet.xml")
     */
    public FileCollection getRuleSetFiles() {
        return ruleSetFiles;
    }

    public void setRuleSetFiles(FileCollection ruleSetFiles) {
        this.ruleSetFiles = ruleSetFiles;
    }

    /**
     * Convenience method for adding rule set files.
     *
     * Example: ruleSetFiles "config/pmd/myRuleSet.xml"
     *
     * @param ruleSetFiles the rule set files to be added
     */
    public void ruleSetFiles(Object... ruleSetFiles) {
        this.ruleSetFiles.add(project.files(ruleSetFiles));
    }

    /**
     * Whether or not to write PMD results to {@code System.out}.
     */
    @Incubating
    public boolean isConsoleOutput() {
        return consoleOutput;
    }

    @Incubating
    public void setConsoleOutput(boolean consoleOutput) {
        this.consoleOutput = consoleOutput;
    }
}
