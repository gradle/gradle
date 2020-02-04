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

import javax.annotation.CheckReturnValue;
import java.util.List;

import static org.gradle.internal.deprecation.Messages.pleaseUseThisMethodInstead;
import static org.gradle.internal.deprecation.Messages.thisIsScheduledToBeRemoved;
import static org.gradle.internal.deprecation.Messages.thisWillBecomeAnError;

@CheckReturnValue
public class DeprecationMessageBuilder<T extends DeprecationMessageBuilder<T>> {

    private String summary;
    private String removalDetails;
    private String context;
    private String advice;
    private Documentation documentation = Documentation.NO_DOCUMENTATION;
    private DeprecatedFeatureUsage.Type usageType = DeprecatedFeatureUsage.Type.USER_CODE_DIRECT;

    DeprecationMessageBuilder() {
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
     * Allows proceeding to terminal {@link WithDocumentation#nagUser()} operation without including any documentation reference.
     * Consider using one of the documentation providing methods instead.
     */
    public WithDocumentation undocumented() {
        return new WithDocumentation(this);
    }

    /**
     * Output: See USER_MANUAL_URL for more details.
     */
    public WithDocumentation withUserManual(String documentationId) {
        this.documentation = Documentation.userManual(documentationId);
        return new WithDocumentation(this);
    }

    /**
     * Output: See USER_MANUAL_URL for more details.
     */
    public WithDocumentation withUserManual(String documentationId, String section) {
        this.documentation = Documentation.userManual(documentationId, section);
        return new WithDocumentation(this);
    }

    /**
     * Output: See DSL_REFERENCE_URL for more details.
     */
    public WithDocumentation withDslReference(Class<?> targetClass, String property) {
        this.documentation = Documentation.dslReference(targetClass, property);
        return new WithDocumentation(this);
    }

    /**
     * Output: Consult the upgrading guide for further information: UPGRADE_GUIDE_URL
     */
    public WithDocumentation withUpgradeGuideSection(int majorVersion, String upgradeGuideSection) {
        this.documentation = Documentation.upgradeGuide(majorVersion, upgradeGuideSection);
        return new WithDocumentation(this);
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

    void setRemovalDetails(String removalDetails) {
        this.removalDetails = removalDetails;
    }

    void setDocumentation(Documentation documentation) {
        this.documentation = documentation;
    }

    DeprecationMessage build() {
        return new DeprecationMessage(summary, removalDetails, advice, context, documentation, usageType);
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

        String removalDetails() {
            return thisIsScheduledToBeRemoved();
        }

        @Override
        DeprecationMessage build() {
            setSummary(formatSummary(formatSubject()));
            setRemovalDetails(removalDetails());
            if (replacement != null) {
                setAdvice(formatAdvice(replacement));
            }
            return super.build();
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

        /**
         * Output: See DSL_REFERENCE_URL for more details.
         */
        public WithDocumentation withDslReference() {
            setDocumentation(Documentation.dslReference(propertyClass, property));
            return new WithDocumentation(this);
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
            return String.format("Please %s the %s configuration instead.", deprecationType.usage, Joiner.on(" or ").join(replacements));
        }

        @Override
        String removalDetails() {
            return thisWillBecomeAnError();
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
            return pleaseUseThisMethodInstead(replacement);
        }

        @Override
        String removalDetails() {
            return thisWillBecomeAnError();
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
}
