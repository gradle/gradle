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
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.problems.interfaces.DocLink;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Map.Entry;

public abstract class Documentation implements DocLink {
    public static final String RECOMMENDATION = "For more %s, please refer to %s in the Gradle documentation.";
    private static final DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry();

    public static final Documentation NO_DOCUMENTATION = new NullDocumentation();

    public static Documentation userManual(String id, String section) {
        return new UserGuide(id, section);
    }

    static Documentation userManual(String id) {
        return new UserGuide(id, null);
    }

    static Documentation upgradeGuide(int majorVersion, String upgradeGuideSection) {
        return new UpgradeGuide(majorVersion, upgradeGuideSection);
    }

    public static Documentation dslReference(Class<?> targetClass, String property) {
        return new DslReference(targetClass, property);
    }

    @Nullable
    public String consultDocumentationMessage() {
        return String.format(RECOMMENDATION, "information", url());
    }

    private static abstract class SerializerableDocumentation extends Documentation {
        abstract Map<String, String> getProperties();
    }

    public static abstract class AbstractBuilder<T> {
        public abstract T withDocumentation(DocLink documentation);

        /**
         * Allows proceeding without including any documentation reference.
         * Consider using one of the documentation providing methods instead.
         */
        public T undocumented() {
            return withDocumentation(Documentation.NO_DOCUMENTATION);
        }

        /**
         * Output: See USER_MANUAL_URL for more details.
         */
        public T withUserManual(String documentationId) {
            return withDocumentation(Documentation.userManual(documentationId));
        }

        /**
         * Output: See USER_MANUAL_URL for more details.
         */
        public T withUserManual(String documentationId, String section) {
            return withDocumentation(Documentation.userManual(documentationId, section));
        }

        /**
         * Output: See DSL_REFERENCE_URL for more details.
         */
        public T withDslReference(Class<?> targetClass, String property) {
            return withDocumentation(Documentation.dslReference(targetClass, property));
        }

        /**
         * Output: Consult the upgrading guide for further information: UPGRADE_GUIDE_URL
         */
        public T withUpgradeGuideSection(int majorVersion, String upgradeGuideSection) {
            return withDocumentation(Documentation.upgradeGuide(majorVersion, upgradeGuideSection));
        }
    }

    private static class NullDocumentation extends SerializerableDocumentation {

        private NullDocumentation() {
        }

        @Override
        public String url() {
            return null;
        }

        @Override
        public String consultDocumentationMessage() {
            return null;
        }

        @Override
        Map<String, String> getProperties() {
            return ImmutableMap.of();
        }
    }

    private static class UserGuide extends SerializerableDocumentation {
        private final String page;
        private final String section;

        private final String topic;

        private UserGuide(String id, @Nullable String section) {
            this.page = Preconditions.checkNotNull(id);
            this.section = section;
            this.topic = null;
        }

        private UserGuide(String topic, String id, @Nullable String section) {
            this.page = Preconditions.checkNotNull(id);
            this.section = section;
            this.topic = topic;
        }

        @Override
        public String url() {
            if (section == null) {
                return DOCUMENTATION_REGISTRY.getDocumentationFor(page);
            }
            if (topic == null) {
                return DOCUMENTATION_REGISTRY.getDocumentationFor(page, section);
            }
            return DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor(topic, page, section);
        }

        @Override
        Map<String, String> getProperties() {
            ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String>builder();
            builder.put("page", page);
            builder.put("section", section);
            if (topic != null) {
                builder.put("topic", topic);
            }
            return builder.build();
        }
    }

    private static class UpgradeGuide extends UserGuide {

        private UpgradeGuide(int majorVersion, String section) {
            super("upgrading_version_" + majorVersion, section);
        }

        @Override
        public String consultDocumentationMessage() {
            return "Consult the upgrading guide for further information: " + url();
        }
    }

    private static class DslReference extends SerializerableDocumentation {
        private final Class<?> targetClass;
        private final String property;

        public DslReference(Class<?> targetClass, String property) {
            this.targetClass = Preconditions.checkNotNull(targetClass);
            this.property = Preconditions.checkNotNull(property);
        }

        @Override
        public String url() {
            return DOCUMENTATION_REGISTRY.getDslRefForProperty(targetClass, property);
        }

        @Override
        Map<String, String> getProperties() {
            return ImmutableMap.of("property", property, "targetClass", targetClass.getName());
        }
    }

    public static class DocLinkJsonDeserializer implements JsonDeserializer<DocLink> {
        @Override
        public DocLink deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = (JsonObject) json;
            JsonElement page = jsonObject.get("page");
            JsonElement section = jsonObject.get("section");
            if (page == null && section == null) {
                return NO_DOCUMENTATION;
            }

            return userManual(page.getAsString(), section.getAsString());
        }
    }

    public static class DocLinkJsonSerializer implements JsonSerializer<DocLink> {
        @Override
        public JsonElement serialize(DocLink src, Type typeOfSrc, JsonSerializationContext context) {
            final JsonObject jsonObject = new JsonObject();
            if (src instanceof SerializerableDocumentation) {
                SerializerableDocumentation sd = (SerializerableDocumentation) src;
                for (Entry<String, String> entry : sd.getProperties().entrySet()) {
                    if (entry.getValue() != null) {
                        jsonObject.addProperty(entry.getKey(), entry.getValue());
                    }
                }
            }
            return jsonObject;
        }
    }
}



