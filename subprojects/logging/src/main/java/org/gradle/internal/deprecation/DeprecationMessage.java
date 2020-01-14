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
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.featurelifecycle.DeprecatedFeatureUsage;

import java.util.List;

import static org.gradle.internal.deprecation.Messages.pleaseUseThisMethodInstead;
import static org.gradle.internal.deprecation.Messages.pluginHasBeenDeprecated;
import static org.gradle.internal.deprecation.Messages.propertyHasBeenDeprecated;
import static org.gradle.internal.deprecation.Messages.thisBehaviourHasBeenDeprecatedAndIsScheduledToBeRemoved;
import static org.gradle.internal.deprecation.Messages.thisHasBeenDeprecatedAndIsScheduledToBeRemoved;
import static org.gradle.internal.deprecation.Messages.thisIsScheduledToBeRemoved;
import static org.gradle.internal.deprecation.Messages.thisWillBecomeAnError;
import static org.gradle.internal.deprecation.Messages.usingMethodHasBeenDeprecated;
import static org.gradle.internal.deprecation.Messages.xHasBeenDeprecated;

public class DeprecationMessage {

    private static final DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry();

    private final String summary;
    private final String removalDetails;
    private final String advice;
    private final String contextualAdvice;
    private final String documentationReference;
    private final DeprecatedFeatureUsage.Type usageType;

    public DeprecationMessage(String summary, String removalDetails, String advice, String contextualAdvice, String documentationReference, DeprecatedFeatureUsage.Type usageType) {
        this.summary = summary;
        this.removalDetails = removalDetails;
        this.advice = advice;
        this.contextualAdvice = contextualAdvice;
        this.documentationReference = documentationReference;
        this.usageType = usageType;
    }

    public static class Builder {
        private String summary;
        private String removalDetails;
        private String advice;
        private String contextualAdvice;
        private String documentationReference;

        private DeprecatedFeatureUsage.Type usageType = DeprecatedFeatureUsage.Type.USER_CODE_DIRECT;

        public Builder withSummary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder withRemovalDetails(String removalDetails) {
            this.removalDetails = removalDetails;
            return this;
        }

        public Builder withAdvice(String advice) {
            this.advice = advice;
            return this;
        }

        public Builder withContextualAdvice(String contextualAdvice) {
            this.contextualAdvice = contextualAdvice;
            return this;
        }

        public Builder withDocumentationReference(String documentationReference) {
            this.documentationReference = documentationReference;
            return this;
        }

        public Builder withIndirectUsage() {
            this.usageType = DeprecatedFeatureUsage.Type.USER_CODE_INDIRECT;
            return this;
        }

        public Builder withBuildInvocation() {
            this.usageType = DeprecatedFeatureUsage.Type.BUILD_INVOCATION;
            return this;
        }

        public void nagUser() {
            DeprecationLogger.nagUserWith(this, DeprecationMessage.Builder.class);
        }

        DeprecationMessage build() {
            return new DeprecationMessage(summary, removalDetails, advice, contextualAdvice, documentationReference, usageType);
        }

    }

    // Output: ${summary}. This has been deprecated and is scheduled to be removed in Gradle {X}.
    public static DeprecationMessage.Builder thisHasBeenDeprecated(final String summary) {
        return new DeprecationMessage.Builder() {
            @Override
            DeprecationMessage build() {
                withSummary(summary);
                withRemovalDetails(thisHasBeenDeprecatedAndIsScheduledToBeRemoved());
                return super.build();
            }
        };
    }

    // Output: ${thing} has been deprecated. This is scheduled to be removed in Gradle {X}.
    public static DeprecationMessage.Builder specificThingHasBeenDeprecated(final String thing) {
        return new DeprecationMessage.Builder() {
            @Override
            DeprecationMessage build() {
                withSummary(xHasBeenDeprecated(thing));
                withRemovalDetails(thisIsScheduledToBeRemoved());
                return super.build();
            }
        };
    }

    // Output: ${thing} has been deprecated. This is scheduled to be removed in Gradle {X}.
    public static DeprecationMessage.Builder indirectCodeUsageHasBeenDeprecated(String thing) {
        return specificThingHasBeenDeprecated(thing).withIndirectUsage();
    }

    // Output: ${feature} has been deprecated. This is scheduled to be removed in Gradle {X}.
    public static DeprecationMessage.Builder deprecatedBuildInvocationFeature(String feature) {
        return specificThingHasBeenDeprecated(feature).withBuildInvocation();
    }

    // Output: ${behaviour}. This behaviour has been deprecated and is scheduled to be removed in Gradle {X}.
    public static DeprecationMessage.Builder behaviourHasBeenDeprecated(final String behaviour) {
        return new DeprecationMessage.Builder() {
            @Override
            DeprecationMessage build() {
                withSummary(behaviour);
                withRemovalDetails(thisBehaviourHasBeenDeprecatedAndIsScheduledToBeRemoved());
                return super.build();
            }
        };
    }

    // Output: The ${parameterName} named parameter has been deprecated. This is scheduled to be removed in Gradle {X}. Please use the ${replacement} named parameter instead.
    public static DeprecationMessage.Builder replacedNamedParameter(final String parameterName, final String replacement) {
        return new DeprecationMessage.Builder() {
            @Override
            DeprecationMessage build() {
                withSummary(String.format("The %s named parameter has been deprecated.", parameterName));
                withRemovalDetails(thisIsScheduledToBeRemoved());
                withAdvice(String.format("Please use the %s named parameter instead.", replacement));
                return super.build();
            }
        };
    }

    public static class DeprecatePropertyBuilder extends Builder {
        private final String property;
        private String replacement;

        DeprecatePropertyBuilder(String property) {
            this.property = property;
        }

        // Output: The ${property} property has been deprecated. This is scheduled to be removed in Gradle {X}. Please use the ${replacement} property instead.
        public DeprecatePropertyBuilder replaceWith(String replacement) {
            this.replacement = replacement;
            return this;
        }

        @Override
        DeprecationMessage build() {
            withSummary(propertyHasBeenDeprecated(property));
            withRemovalDetails(thisIsScheduledToBeRemoved());
            if (replacement != null) {
                withAdvice(String.format("Please use the %s property instead.", replacement));
            }
            return super.build();
        }
    }

    public static DeprecationMessage.Builder replacedConfiguration(final String configurationName, final ConfigurationDeprecationType deprecationType, final List<String> replacements) {
        return new DeprecationMessage.Builder() {
            @Override
            DeprecationMessage build() {
                withSummary(String.format("The %s configuration has been deprecated for %s.", configurationName, deprecationType.displayName()));
                withRemovalDetails(thisWillBecomeAnError());
                withAdvice(String.format("Please %s the %s configuration instead.", deprecationType.usage, Joiner.on(" or ").join(replacements)));
                if (!deprecationType.inUserCode) {
                    withIndirectUsage();
                }
                return super.build();
            }
        };
    }

    public static class DeprecateMethodBuilder extends Builder {
        private final String method;
        private String replacement;

        DeprecateMethodBuilder(String method) {
            this.method = method;
        }

        // Output: The ${method} method has been deprecated. This is scheduled to be removed in Gradle {X}. Please use the ${replacement} method instead.
        public DeprecateMethodBuilder replaceWith(String replacement) {
            this.replacement = replacement;
            return this;
        }

        @Override
        DeprecationMessage build() {
            withSummary(String.format("The %s method has been deprecated.", method));
            withRemovalDetails(thisIsScheduledToBeRemoved());
            if (replacement != null) {
                withAdvice(pleaseUseThisMethodInstead(replacement));
            }
            return super.build();
        }
    }

    // Output: Using method ${methodName} has been deprecated. This will fail with an error in Gradle {X}.
    public static DeprecationMessage.Builder discontinuedMethodInvocation(final String invocation) {
        return new DeprecationMessage.Builder() {
            @Override
            DeprecationMessage build() {
                withSummary(usingMethodHasBeenDeprecated(invocation));
                withRemovalDetails(thisWillBecomeAnError());
                return super.build();
            }
        };
    }

    // Use for some operation that is not deprecated, but something about the method parameters or state is deprecated.
    // Output: Using method ${methodName} has been deprecated. This will fail with an error in Gradle {X}. Please use the ${replacement} method instead.
    public static DeprecationMessage.Builder replacedMethodInvocation(final String invocation, final String replacement) {
        return new DeprecationMessage.Builder() {
            @Override
            DeprecationMessage build() {
                withSummary(usingMethodHasBeenDeprecated(invocation));
                withRemovalDetails(thisWillBecomeAnError());
                withAdvice(pleaseUseThisMethodInstead(replacement));
                return super.build();
            }
        };
    }

    // Use for some operation that is not deprecated, but something about the method parameters or state is deprecated.
    // Output: ${invocation} has been deprecated. This will fail with an error in Gradle {X}.
    public static DeprecationMessage.Builder discontinuedInvocation(final String invocation) {
        return new DeprecationMessage.Builder() {
            @Override
            DeprecationMessage build() {
                withSummary(xHasBeenDeprecated(invocation));
                withRemovalDetails(thisWillBecomeAnError());
                return super.build();
            }
        };
    }

    // Output: The ${taskName} task type has been deprecated. This is scheduled to be removed in Gradle X. Please use the ${replacement} instead.
    public static DeprecationMessage.Builder replacedTaskType(final String taskName, final String replacement) {
        return new DeprecationMessage.Builder() {
            @Override
            DeprecationMessage build() {
                withSummary(String.format("The %s task type has been deprecated.", taskName));
                withRemovalDetails(thisIsScheduledToBeRemoved());
                withAdvice(String.format("Please use the %s instead.", replacement));
                return super.build();
            }
        };
    }

    public static class DeprecateTaskBuilder extends Builder {
        private final String task;
        private String replacement;

        DeprecateTaskBuilder(String task) {
            this.task = task;
        }

        // Output: The ${taskName} task has been deprecated. This is scheduled to be removed in Gradle X. Please use the ${replacement} task instead.
        public DeprecateTaskBuilder replaceWith(String replacement) {
            this.replacement = replacement;
            return this;
        }

        @Override
        DeprecationMessage build() {
            withSummary(String.format("The %s task has been deprecated.", task));
            withRemovalDetails(thisIsScheduledToBeRemoved());
            if (replacement != null) {
                withAdvice(String.format("Please use the %s task instead.", replacement));
            }
            return super.build();
        }
    }

    // Output: The ${toolName} has been deprecated. This is scheduled to be removed in Gradle X. Consider using ${replacement} instead.
    public static DeprecationMessage.Builder toolReplacedWithExternalOne(final String toolName, final String replacement) {
        return new DeprecationMessage.Builder() {
            @Override
            DeprecationMessage build() {
                withSummary(String.format("The %s has been deprecated.", toolName));
                withRemovalDetails(thisIsScheduledToBeRemoved());
                withAdvice(String.format("Consider using %s instead.", replacement));
                return super.build();
            }
        };
    }

    // Output: The ${pluginName} plugin has been deprecated. This is scheduled to be removed in Gradle X. ${advice}
    public static DeprecationMessage.Builder deprecatedPlugin(final String pluginName) {
        return new DeprecationMessage.Builder() {
            @Override
            DeprecationMessage build() {
                withSummary(pluginHasBeenDeprecated(pluginName));
                withRemovalDetails(thisIsScheduledToBeRemoved());
                return super.build();
            }
        };
    }

    // Output: The ${pluginName} plugin has been deprecated. This is scheduled to be removed in Gradle X. Please use the ${replacement} plugin instead.
    public static DeprecationMessage.Builder replacedPlugin(final String pluginName, final String replacement) {
        return new DeprecationMessage.Builder() {
            @Override
            DeprecationMessage build() {
                withSummary(pluginHasBeenDeprecated(pluginName));
                withRemovalDetails(thisIsScheduledToBeRemoved());
                withAdvice(String.format("Please use the %s plugin instead.", replacement));
                return super.build();
            }
        };
    }

    // Output: The ${pluginName} plugin has been deprecated. This is scheduled to be removed in Gradle X. Consider using the ${replacement} plugin instead.
    public static DeprecationMessage.Builder pluginReplacedWithExternalOne(final String pluginName, final String replacement) {
        return new DeprecationMessage.Builder() {
            @Override
            DeprecationMessage build() {
                withSummary(pluginHasBeenDeprecated(pluginName));
                withRemovalDetails(thisIsScheduledToBeRemoved());
                withAdvice(String.format("Consider using the %s plugin instead.", replacement));
                return super.build();
            }
        };
    }

    // TODO: start here with documentation embedding
    // Output: The ${pluginName} plugin has been deprecated. This is scheduled to be removed in Gradle X. Consult the upgrading guide for further information: link-to-user-manual/upgrading_version_${majorVersion}.html#${replacement}
    public static DeprecationMessage.Builder deprecatedPlugin(final String pluginName, final int majorVersion, final String upgradeGuideSection) {
        return new DeprecationMessage.Builder() {
            @Override
            DeprecationMessage build() {
                withSummary(pluginHasBeenDeprecated(pluginName));
                withRemovalDetails(thisIsScheduledToBeRemoved());
                withAdvice("Consult the upgrading guide for further information: " + DOCUMENTATION_REGISTRY.getDocumentationFor("upgrading_version_" + majorVersion, upgradeGuideSection));
                return super.build();
            }
        };
    }

    public DeprecatedFeatureUsage toDeprecatedFeatureUsage(Class<?> calledFrom) {
        return new DeprecatedFeatureUsage(summary, removalDetails, advice, contextualAdvice, usageType, calledFrom);
    }

}
