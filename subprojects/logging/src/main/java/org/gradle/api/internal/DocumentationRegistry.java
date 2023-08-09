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

package org.gradle.api.internal;

import org.gradle.api.problems.interfaces.DocLink;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.GradleVersion;

/**
 * Locates documentation for various features.
 */
@ServiceScope(Scope.Global.class)
public class DocumentationRegistry {
    public static final String BASE_URL = "https://docs.gradle.org/" + GradleVersion.current().getVersion();
    public static final String DSL_PROPERTY_URL_FORMAT = "%s/dsl/%s.html#%s:%s";
    public static final String LEARN_MORE_STRING = "To learn more about Gradle by exploring our Samples at ";

    /**
     * Returns the location of the documentation for the given feature, referenced by id. The location may be local or remote.
     */
    public String getDocumentationFor(String id) {
        return String.format("%s/userguide/%s.html", BASE_URL, id);
    }


    /**
     * Returns the location of the documentation for the given feature, referenced by id and section. The location may be local or remote.
     */
    public String getDocumentationFor(String id, String section) {
        return getDocumentationFor(id) + "#" + section;
    }

    public String getDslRefForProperty(Class<?> clazz, String property) {
        String className = clazz.getName();
        return String.format(DSL_PROPERTY_URL_FORMAT, BASE_URL, className, className, property);
    }

    public String getDslRefForProperty(String className, String property) {
        return String.format(DSL_PROPERTY_URL_FORMAT, BASE_URL, className, className, property);
    }

    public String getSampleIndex() {
        return BASE_URL + "/samples";
    }

    public String getSampleFor(String id) {
        return String.format(getSampleIndex() + "/sample_%s.html", id);
    }

    public String getSampleForMessage(String id) {
        return LEARN_MORE_STRING + getSampleFor(id);
    }

    public String getSampleForMessage() {
        return LEARN_MORE_STRING + getSampleIndex();
    }

    public String getDocumentationRecommendationFor(String topic, String id) {
        return getRecommendationString(topic, getDocumentationFor(id));
    }

    public String getDocumentationRecommendationFor(String topic, String id, String section) {
        return getRecommendationString(topic, getDocumentationFor(id, section));
    }

    public String getDocumentationRecommendationFor(String topic, DocLink docLink) {
        String url = docLink.documentationUrl();
        return getRecommendationString(topic, url == null ? "<N/A>" : url);
    }


    public static final String RECOMMENDATION = "For more %s, please refer to %s in the Gradle documentation.";

    private static String getRecommendationString(String topic, String url) {
        return String.format(RECOMMENDATION, topic.trim(), url);
    }
}
