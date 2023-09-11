/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.deprecation;

import com.google.common.base.Joiner;
import org.gradle.api.problems.DocLink;
import org.gradle.api.problems.Problem;
import org.gradle.util.GradleVersion;

import javax.annotation.CheckReturnValue;
import java.util.List;

@CheckReturnValue
public class DeprecationMessageBuilder<T extends DeprecationMessageBuilder<T>> {

    private static final GradleVersion GRADLE9 = GradleVersion.version("9.0");

    private String summary;
    private DeprecationTimeline deprecationTimeline;
    private String context;
    private String advice;
    private DocLink documentation = Documentation.NO_DOCUMENTATION;
    private DeprecatedFeatureUsage.Type usageType = DeprecatedFeatureUsage.Type.USER_CODE_DIRECT;

    DeprecationMessageBuilder() {
    }

    public static WithDocumentation withDocumentation(Problem warning, WithDeprecationTimeline withDeprecationTimeline) {
        DocLink docLink = warning.getDocumentationLink();
        if (docLink != null) {
            return withDeprecationTimeline
                .withDocumentation(warning.getDocumentationLink());
        }
        return withDeprecationTimeline.undocumented();
    }

    @SuppressWarnings("unchecked")
    public T withContext(String context) {
        this.context = context;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T withAdvice(String advice) {
        this.advice = advice;
        return (T) this;
    }

    /**
     * Output: This is scheduled to be removed in Gradle 9.0.
     */
    public WithDeprecationTimeline willBeRemovedInGradle9() {
        this.deprecationTimeline = DeprecationTimeline.willBeRemovedInVersion(GRADLE9);
        return new WithDeprecationTimeline(this);
    }

    /**
     * Output: This will fail with an error in Gradle 9.0.
     */
    public WithDeprecationTimeline willBecomeAnErrorInGradle9() {
        this.deprecationTimeline = DeprecationTimeline.willBecomeAnErrorInVersion(GRADLE9);
        return new WithDeprecationTimeline(this);
    }

    /**
     * Output: Starting with Gradle 9.0, ${message}.
     */
    public WithDeprecationTimeline startingWithGradle9(String message) {
        this.deprecationTimeline = DeprecationTimeline.startingWithVersion(GRADLE9, message);
        return new WithDeprecationTimeline(this);
    }

    void setIndirectUsage() {
        this.usageType = DeprecatedFeatureUsage.Type.USER_CODE_INDIRECT;
    }

    void setBuildInvocationUsage() {
        this.usageType = DeprecatedFeatureUsage.Type.BUILD_INVOCATION;
    }

    void setSummary(String summary) {
        this.summary = summary;
    }

    void setAdvice(String advice) {
        this.advice = advice;
    }

    void setDeprecationTimeline(DeprecationTimeline deprecationTimeline) {
        this.deprecationTimeline = deprecationTimeline;
    }

    void setDocumentation(DocLink documentation) {
        this.documentation = documentation;
    }

    DeprecationMessage build() {
        return new DeprecationMessage(summary, deprecationTimeline.toString(), advice, context, documentation, usageType);
    }

    public static class WithDeprecationTimeline extends Documentation.AbstractBuilder<WithDocumentation> {
        private final DeprecationMessageBuilder<?> builder;

        public WithDeprecationTimeline(DeprecationMessageBuilder<?> builder) {
            this.builder = builder;
        }

        @Override
        public WithDocumentation withDocumentation(DocLink documentation) {
            builder.setDocumentation(documentation);
            return new WithDocumentation(builder);
        }
    }

    public static class WithDocumentation {
        private final DeprecationMessageBuilder<?> builder;

        WithDocumentation(DeprecationMessageBuilder<?> builder) {
            this.builder = builder;
        }

        /**
         * Terminal operation. Emits the deprecation message.
         */
        public void nagUser() {
            DeprecationLogger.nagUserWith(builder, WithDocumentation.class);
        }
    }

    public static abstract class WithReplacement<T, SELF extends WithReplacement<T, SELF>> extends DeprecationMessageBuilder<SELF> {
        private final String subject;
        private T replacement;

        WithReplacement(String subject) {
            this.subject = subject;
        }

        /**
         * Constructs advice message based on the context.
         *
         * deprecateProperty: Please use the ${replacement} property instead.
         * deprecateMethod/deprecateInvocation: Please use the ${replacement} method instead.
         * deprecatePlugin: Please use the ${replacement} plugin instead.
         * deprecateTask: Please use the ${replacement} task instead.
         * deprecateInternalApi: Please use ${replacement} instead.
         * deprecateNamedParameter: Please use the ${replacement} named parameter instead.
         */
        @SuppressWarnings("unchecked")
        public SELF replaceWith(T replacement) {
            this.replacement = replacement;
            return (SELF) this;
        }

        String formatSubject() {
            return subject;
        }

        abstract String formatSummary(String subject);

        abstract String formatAdvice(T replacement);


        @Override
        DeprecationMessage build() {
            setSummary(formatSummary(formatSubject()));
            if (replacement != null) {
                setAdvice(formatAdvice(replacement));
            }
            return super.build();
        }
    }

    public static class DeprecateAction extends WithReplacement<String, DeprecateAction> {
        DeprecateAction(String subject) {
            super(subject);
        }

        @Override
        String formatSummary(String subject) {
            return String.format("%s has been deprecated.", subject);
        }

        @Override
        String formatAdvice(String replacement) {
            return String.format("Please use %s instead.", replacement);
        }
    }

    public static class DeprecateNamedParameter extends WithReplacement<String, DeprecateNamedParameter> {

        DeprecateNamedParameter(String parameter) {
            super(parameter);
        }

        @Override
        String formatSummary(String parameter) {
            return String.format("The %s named parameter has been deprecated.", parameter);
        }

        @Override
        String formatAdvice(String replacement) {
            return String.format("Please use the %s named parameter instead.", replacement);
        }
    }

    public static class DeprecateProperty extends WithReplacement<String, DeprecateProperty> {
        private final Class<?> propertyClass;
        private final String property;

        DeprecateProperty(Class<?> propertyClass, String property) {
            super(property);
            this.propertyClass = propertyClass;
            this.property = property;
        }

        @Override
        public WithDeprecationTimeline willBeRemovedInGradle9() {
            setDeprecationTimeline(DeprecationTimeline.willBeRemovedInVersion(GRADLE9));
            return new WithDeprecationTimeline(this);
        }

        public class WithDeprecationTimeline extends DeprecationMessageBuilder.WithDeprecationTimeline {
            private final DeprecateProperty builder;

            public WithDeprecationTimeline(DeprecateProperty builder) {
                super(builder);
                this.builder = builder;
            }

            /**
             * Output: See DSL_REFERENCE_URL for more details.
             */
            public WithDocumentation withDslReference() {
                setDocumentation(Documentation.dslReference(propertyClass, property));
                return new WithDocumentation(builder);
            }
        }

        @Override
        String formatSubject() {
            return String.format("%s.%s", propertyClass.getSimpleName(), property);
        }

        @Override
        String formatSummary(String property) {
            return String.format("The %s property has been deprecated.", property);
        }

        @Override
        String formatAdvice(String replacement) {
            return String.format("Please use the %s property instead.", replacement);
        }
    }

    public static class DeprecateSystemProperty extends WithReplacement<String, DeprecateSystemProperty> {
        private final String systemProperty;

        DeprecateSystemProperty(String systemProperty) {
            super(systemProperty);
            this.systemProperty = systemProperty;
            // This never happens in user code
            setIndirectUsage();
        }

        @Override
        String formatSubject() {
            return systemProperty;
        }

        @Override
        String formatSummary(String property) {
            return String.format("The %s system property has been deprecated.", property);
        }

        @Override
        String formatAdvice(String replacement) {
            return String.format("Please use the %s system property instead.", replacement);
        }
    }

    @CheckReturnValue
    public static class ConfigurationDeprecationTypeSelector {
        private final String configuration;

        ConfigurationDeprecationTypeSelector(String configuration) {
            this.configuration = configuration;
        }

        public DeprecateConfiguration forArtifactDeclaration() {
            return new DeprecateConfiguration(configuration, ConfigurationDeprecationType.ARTIFACT_DECLARATION);
        }

        public DeprecateConfiguration forConsumption() {
            return new DeprecateConfiguration(configuration, ConfigurationDeprecationType.CONSUMPTION);
        }

        public DeprecateConfiguration forDependencyDeclaration() {
            return new DeprecateConfiguration(configuration, ConfigurationDeprecationType.DEPENDENCY_DECLARATION);
        }

        public DeprecateConfiguration forResolution() {
            return new DeprecateConfiguration(configuration, ConfigurationDeprecationType.RESOLUTION);
        }
    }

    public static class DeprecateConfiguration extends WithReplacement<List<String>, DeprecateConfiguration> {
        private final ConfigurationDeprecationType deprecationType;

        DeprecateConfiguration(String configuration, ConfigurationDeprecationType deprecationType) {
            super(configuration);
            this.deprecationType = deprecationType;
            if (!deprecationType.inUserCode) {
                setIndirectUsage();
            }
        }

        @Override
        String formatSummary(String configuration) {
            return String.format("The %s configuration has been deprecated for %s.", configuration, deprecationType.displayName());
        }

        @Override
        String formatAdvice(List<String> replacements) {
            if (replacements.isEmpty()) {
                return "Please " + deprecationType.usage + " another configuration instead.";
            }
            return String.format("Please %s the %s configuration instead.", deprecationType.usage, Joiner.on(" or ").join(replacements));
        }
    }

    public static class DeprecateMethod extends WithReplacement<String, DeprecateMethod> {
        private final Class<?> methodClass;
        private final String methodWithParams;

        DeprecateMethod(Class<?> methodClass, String methodWithParams) {
            super(methodWithParams);
            this.methodClass = methodClass;
            this.methodWithParams = methodWithParams;
        }

        @Override
        String formatSubject() {
            return String.format("%s.%s", methodClass.getSimpleName(), methodWithParams);
        }

        @Override
        String formatSummary(String method) {
            return String.format("The %s method has been deprecated.", method);
        }

        @Override
        String formatAdvice(String replacement) {
            return pleaseUseThisMethodInstead(replacement);
        }

        private static String pleaseUseThisMethodInstead(String replacement) {
            return String.format("Please use the %s method instead.", replacement);
        }
    }

    public static class DeprecateInvocation extends WithReplacement<String, DeprecateInvocation> {

        DeprecateInvocation(String invocation) {
            super(invocation);
        }

        @Override
        String formatSummary(String invocation) {
            return String.format("Using method %s has been deprecated.", invocation);
        }

        @Override
        String formatAdvice(String replacement) {
            return DeprecateMethod.pleaseUseThisMethodInstead(replacement);
        }
    }

    public static class DeprecateType extends WithReplacement<String, DeprecateType> {

        DeprecateType(String type) {
            super(type);
        }

        @Override
        String formatSummary(String type) {
            return String.format("The %s type has been deprecated.", type);
        }

        @Override
        String formatAdvice(String replacement) {
            return String.format("Please use the %s type instead.", replacement);
        }
    }

    public static class DeprecateTask extends WithReplacement<String, DeprecateTask> {
        DeprecateTask(String task) {
            super(task);
        }

        @Override
        String formatSummary(String task) {
            return String.format("The %s task has been deprecated.", task);
        }

        @Override
        String formatAdvice(String replacement) {
            return String.format("Please use the %s task instead.", replacement);
        }
    }

    public static class DeprecateTaskType extends WithReplacement<Class<?>, DeprecateTaskType> {
        private final String path;

        DeprecateTaskType(String task, String path) {
            super(task);
            this.path = path;
        }

        @Override
        String formatSummary(String type) {
            return String.format("The task type %s (used by the %s task) has been deprecated.", type, path);
        }

        @Override
        String formatAdvice(Class<?> replacement) {
            return String.format("Please use the %s type instead.", replacement.getCanonicalName());
        }
    }

    public static class DeprecatePlugin extends WithReplacement<String, DeprecatePlugin> {

        private boolean externalReplacement = false;

        DeprecatePlugin(String plugin) {
            super(plugin);
        }

        @Override
        String formatSummary(String plugin) {
            return String.format("The %s plugin has been deprecated.", plugin);
        }

        @Override
        String formatAdvice(String replacement) {
            return externalReplacement ? String.format("Consider using the %s plugin instead.", replacement) : String.format("Please use the %s plugin instead.", replacement);
        }

        /**
         * Advice output: Consider using the ${replacement} plugin instead.
         */
        public DeprecatePlugin replaceWithExternalPlugin(String replacement) {
            this.externalReplacement = true;
            return replaceWith(replacement);
        }
    }

    public static class DeprecateInternalApi extends WithReplacement<String, DeprecateInternalApi> {
        DeprecateInternalApi(String api) {
            super(api);
        }

        @Override
        String formatSummary(String api) {
            return String.format("Internal API %s has been deprecated.", api);
        }

        @Override
        String formatAdvice(String replacement) {
            return String.format("Please use %s instead.", replacement);
        }
    }

    public static class DeprecateBehaviour extends DeprecationMessageBuilder<DeprecateBehaviour> {

        private final String behaviour;

        public DeprecateBehaviour(String behaviour) {
            this.behaviour = behaviour;
        }

        /**
         * Output: This behavior is scheduled to be removed in Gradle 9.0.
         */
        public WithDeprecationTimeline willBeRemovedInGradle9() {
            setDeprecationTimeline(DeprecationTimeline.behaviourWillBeRemovedInVersion(GRADLE9));
            return new WithDeprecationTimeline(this);
        }

        @Override
        DeprecationMessage build() {
            setSummary(String.format("%s This behavior has been deprecated.", behaviour));
            return super.build();
        }
    }
}
