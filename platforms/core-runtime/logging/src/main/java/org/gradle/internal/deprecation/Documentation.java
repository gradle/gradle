/*
 * Copyright 2023 the original author or authors.
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

import com.google.common.base.Preconditions;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.problems.internal.DocLink;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

public abstract class Documentation implements DocLink {
    public static final String RECOMMENDATION = "For more %s, please refer to %s in the Gradle documentation.";
    private static final DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry();

    public static Documentation userManual(String id, String section) {
        return new UserGuide(id, section);
    }

    public static Documentation userManual(String id) {
        return new UserGuide(id, null);
    }

    static Documentation upgradeGuide(int majorVersion, String upgradeGuideSection) {
        return new UpgradeGuide(majorVersion, upgradeGuideSection);
    }

    public static Documentation dslReference(Class<?> targetClass, String property) {
        return new DslReference(targetClass, property);
    }

    public static Documentation kotlinDslExtensionReference(String extensionName) {
        return new KotlinDslExtensionReference(extensionName);
    }

    @Override
    public String getConsultDocumentationMessage() {
        return String.format(RECOMMENDATION, "information", getUrl());
    }

    private static abstract class SerializableDocumentation extends Documentation {
    }

    public static abstract class AbstractBuilder<T> {
        public abstract T withDocumentation(@Nullable DocLink documentation);

        /**
         * Allows proceeding without including any documentation reference.
         * Consider using one of the documentation providing methods instead.
         */
        @CheckReturnValue
        public T undocumented() {
            return withDocumentation(null);
        }

        /**
         * Output: See USER_MANUAL_URL for more details.
         */
        @CheckReturnValue
        public T withUserManual(String documentationId) {
            return withDocumentation(Documentation.userManual(documentationId));
        }

        /**
         * Output: See USER_MANUAL_URL for more details.
         */
        @CheckReturnValue
        public T withUserManual(String documentationId, String section) {
            return withDocumentation(Documentation.userManual(documentationId, section));
        }

        /**
         * Output: See DSL_REFERENCE_URL for more details.
         */
        @CheckReturnValue
        public T withDslReference(Class<?> targetClass, String property) {
            return withDocumentation(Documentation.dslReference(targetClass, property));
        }

        /**
         * Output: Consult the upgrading guide for further information: UPGRADE_GUIDE_URL
         */
        @CheckReturnValue
        public T withUpgradeGuideSection(int majorVersion, String upgradeGuideSection) {
            return withDocumentation(Documentation.upgradeGuide(majorVersion, upgradeGuideSection));
        }
    }

    private static class UserGuide extends SerializableDocumentation {
        private final String page;
        private final String section;

        private final String topic;

        private UserGuide(String id, @Nullable String section) {
            this.page = Preconditions.checkNotNull(id);
            this.section = section;
            this.topic = null;
        }

        @Override
        public String getUrl() {
            if (section == null) {
                return DOCUMENTATION_REGISTRY.getDocumentationFor(page);
            }
            if (topic == null) {
                return DOCUMENTATION_REGISTRY.getDocumentationFor(page, section);
            }
            return DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor(topic, page, section);
        }

    }

    private static class UpgradeGuide extends UserGuide {

        private UpgradeGuide(int majorVersion, String section) {
            super("upgrading_version_" + majorVersion, section);
        }

        @Override
        public String getConsultDocumentationMessage() {
            return "Consult the upgrading guide for further information: " + getUrl();
        }
    }

    private static class DslReference extends SerializableDocumentation {
        private final Class<?> targetClass;
        private final String property;

        public DslReference(Class<?> targetClass, String property) {
            this.targetClass = Preconditions.checkNotNull(targetClass);
            this.property = Preconditions.checkNotNull(property);
        }

        @Override
        public String getUrl() {
            return DOCUMENTATION_REGISTRY.getDslRefForProperty(targetClass, property);
        }

    }

    private static class KotlinDslExtensionReference extends SerializableDocumentation {
        private final String extensionName;

        public KotlinDslExtensionReference(String extensionName) {
            this.extensionName = extensionName;
        }

        @Override
        public String getUrl() {
            return DOCUMENTATION_REGISTRY.getKotlinDslRefForExtension(extensionName);
        }

    }

}



