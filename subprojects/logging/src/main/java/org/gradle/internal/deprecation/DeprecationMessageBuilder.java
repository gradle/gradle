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
public class DeprecationMessageBuilder {

    private String summary;
    private String removalDetails;
    private String context;
    private String advice;
    private DocumentationReference documentationReference = DocumentationReference.NO_DOCUMENTATION;
    private DeprecatedFeatureUsage.Type usageType = DeprecatedFeatureUsage.Type.USER_CODE_DIRECT;

    DeprecationMessageBuilder() {
    }

    public DeprecationMessageBuilder withContext(String context) {
        this.context = context;
        return this;
    }

    public DeprecationMessageBuilder withAdvice(String advice) {
        this.advice = advice;
        return this;
    }

    public WithDocumentation undocumented() {
        return new WithDocumentation(this);
    }

    public WithDocumentation guidedBy(String documentationId, String section) {
        this.documentationReference = DocumentationReference.create(documentationId, section);
        return new WithDocumentation(this);
    }

    public WithDocumentation withUpgradeGuideSection(int majorVersion, String upgradeGuideSection) {
        this.documentationReference = DocumentationReference.upgradeGuide(majorVersion, upgradeGuideSection);
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

    DeprecationMessage build() {
        return new DeprecationMessage(summary, removalDetails, advice, context, documentationReference, usageType);
    }

    public static class WithDocumentation {
        private final DeprecationMessageBuilder builder;

        public WithDocumentation(DeprecationMessageBuilder builder) {
            this.builder = builder;
        }

        public void nagUser() {
            DeprecationLogger.nagUserWith(builder, WithDocumentation.class);
        }
    }

    public static abstract class WithReplacement<T> extends DeprecationMessageBuilder {
        private final String subject;
        private T replacement;

        WithReplacement(String subject) {
            this.subject = subject;
        }

        public WithReplacement<T> replaceWith(T replacement) {
            this.replacement = replacement;
            return this;
        }

        abstract String formatSummary(String subject);

        abstract String formatAdvice(T replacement);

        String removalDetails() {
            return thisIsScheduledToBeRemoved();
        }

        @Override
        DeprecationMessage build() {
            setSummary(formatSummary(subject));
            setRemovalDetails(removalDetails());
            if (replacement != null) {
                setAdvice(formatAdvice(replacement));
            }
            return super.build();
        }
    }

    public static class DeprecateNamedParameter extends WithReplacement<String> {

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

    public static class DeprecateProperty extends WithReplacement<String> {

        DeprecateProperty(String property) {
            super(property);
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

    public static class ConfigurationDeprecationTypeSelector {
        private final String configuration;

        ConfigurationDeprecationTypeSelector(String configuration) {
            this.configuration = configuration;
        }

        @CheckReturnValue
        public DeprecateConfiguration forArtifactDeclaration() {
            return new DeprecateConfiguration(configuration, ConfigurationDeprecationType.ARTIFACT_DECLARATION);
        }

        @CheckReturnValue
        public DeprecateConfiguration forConsumption() {
            return new DeprecateConfiguration(configuration, ConfigurationDeprecationType.CONSUMPTION);
        }

        @CheckReturnValue
        public DeprecateConfiguration forDependencyDeclaration() {
            return new DeprecateConfiguration(configuration, ConfigurationDeprecationType.DEPENDENCY_DECLARATION);
        }

        @CheckReturnValue
        public DeprecateConfiguration forResolution() {
            return new DeprecateConfiguration(configuration, ConfigurationDeprecationType.RESOLUTION);
        }
    }

    public static class DeprecateConfiguration extends WithReplacement<List<String>> {
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

    public static class DeprecateMethod extends WithReplacement<String> {

        DeprecateMethod(String method) {
            super(method);
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

    public static class DeprecateInvocation extends WithReplacement<String> {

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

    public static class DeprecateTask extends WithReplacement<String> {
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

    public static class DeprecatePlugin extends WithReplacement<String> {

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

        public DeprecationMessageBuilder replaceWithExternalPlugin(String replacement) {
            this.externalReplacement = true;
            return replaceWith(replacement);
        }
    }

    public static class DeprecateInternalApi extends WithReplacement<String> {
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
