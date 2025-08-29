/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.ProblemGroup;

public abstract class GradleCoreProblemGroup {

    private static final DefaultCompilationProblemGroup COMPILATION_PROBLEM_GROUP = new DefaultCompilationProblemGroup();
    private static final ProblemGroup DEPRECATION_PROBLEM_GROUP = ProblemGroup.create("deprecation", "Deprecation");
    private static final DefaultVerificationProblemGroup VALIDATION_PROBLEM_GROUP = new DefaultVerificationProblemGroup();
    private static final ProblemGroup PLUGIN_APPLICATION_PROBLEM_GROUP = ProblemGroup.create("plugin-application", "Plugin application");
    private static final ProblemGroup TASK_SELECTION_PROBLEM_GROUP = ProblemGroup.create("task-selection", "Task selection");
    private static final ProblemGroup VERSION_CATALOG_PROBLEM_GROUP = ProblemGroup.create("dependency-version-catalog", "Version catalog");
    private static final ProblemGroup VARIANT_RESOLUTION_PROBLEM_GROUP = ProblemGroup.create("dependency-variant-resolution", "Variant resolution");
    private static final ProblemGroup CONFIGURATION_USAGE_PROBLEM_GROUP = ProblemGroup.create("configuration-usage", "Configuration usage");
    private static final DaemonToolchainProblemGroupProvider DAEMON_TOOLCHAIN_PROBLEM_GROUP = new DefaultDaemonToolchainProblemGroup();

    public static CompilationProblemGroupProvider compilation() {
        return COMPILATION_PROBLEM_GROUP;
    }

    public static ProblemGroup deprecation() {
        return DEPRECATION_PROBLEM_GROUP;
    }

    public static VerificationProblemGroupProvider validation() {
        return VALIDATION_PROBLEM_GROUP;
    }

    public static ProblemGroup pluginApplication() {
        return PLUGIN_APPLICATION_PROBLEM_GROUP;
    }

    public static ProblemGroup taskSelection() {
        return TASK_SELECTION_PROBLEM_GROUP;
    }

    public static ProblemGroup versionCatalog() {
        return VERSION_CATALOG_PROBLEM_GROUP;
    }

    public static ProblemGroup variantResolution() {
        return VARIANT_RESOLUTION_PROBLEM_GROUP;
    }

    public static ProblemGroup configurationUsage() {
        return CONFIGURATION_USAGE_PROBLEM_GROUP;
    }

    public static DaemonToolchainProblemGroupProvider daemonToolchain() {
        return DAEMON_TOOLCHAIN_PROBLEM_GROUP;
    }

    public interface ProblemGroupProvider {
        ProblemGroup thisGroup();
        ProblemGroup customGroup(String id, String displayName);
    }

    public interface CompilationProblemGroupProvider extends ProblemGroupProvider {
        ProblemGroup java();
        ProblemGroup groovy();
        ProblemGroup groovyDsl();
    }

    public interface VerificationProblemGroupProvider extends ProblemGroupProvider {
        ProblemGroup property();
        ProblemGroup type();
    }

    public interface DaemonToolchainProblemGroupProvider extends ProblemGroupProvider {
        ProblemGroup configurationGeneration();
    }

    private static class DefaultProblemGroupProvider implements ProblemGroupProvider {

        final ProblemGroup thisGroup;

        private DefaultProblemGroupProvider(String id, String displayName) {
            this.thisGroup = ProblemGroup.create(id, displayName);
        }

        @Override
        public ProblemGroup thisGroup() {
            return thisGroup;
        }

        @Override
        public ProblemGroup customGroup(String id, String displayName){
            return ProblemGroup.create(id, displayName, thisGroup);
        }
    }

    private static class DefaultCompilationProblemGroup extends DefaultProblemGroupProvider implements CompilationProblemGroupProvider {

        private final ProblemGroup java = ProblemGroup.create("java", "Java compilation", thisGroup);
        private final ProblemGroup groovy = ProblemGroup.create("groovy", "Groovy compilation", thisGroup);
        public ProblemGroup groovyDsl = ProblemGroup.create("groovy-dsl", "Groovy DSL script compilation", thisGroup);

        private DefaultCompilationProblemGroup() {
            super("compilation", "Compilation");
        }

        @Override
        public ProblemGroup java() {
            return this.java;
        }

        @Override
        public ProblemGroup groovy() {
            return this.groovy;
        }

        @Override
        public ProblemGroup groovyDsl() {
            return this.groovyDsl;
        }
    }

    private static class DefaultVerificationProblemGroup extends DefaultProblemGroupProvider implements VerificationProblemGroupProvider {

        public static final String VERIFICATION = "verification";
        private final ProblemGroup property = ProblemGroup.create("property-" +VERIFICATION, "Gradle property " +VERIFICATION, thisGroup);
        private final ProblemGroup type = ProblemGroup.create("type-" +VERIFICATION, "Gradle type " + VERIFICATION, thisGroup);

        private DefaultVerificationProblemGroup() {
            super(VERIFICATION, "Verification");
        }

        @Override
        public ProblemGroup property() {
            return property;
        }

        @Override
        public ProblemGroup type() {
            return type;
        }
    }

    private static class DefaultDaemonToolchainProblemGroup extends  DefaultProblemGroupProvider implements DaemonToolchainProblemGroupProvider {

        private final ProblemGroup configurationGeneration = ProblemGroup.create("configuration-generation", "Gradle configuration generation", thisGroup);

        private DefaultDaemonToolchainProblemGroup() {
            super("daemon-toolchain", "Daemon toolchain");
        }

        @Override
        public ProblemGroup configurationGeneration() {
            return configurationGeneration;
        }
    }
}
