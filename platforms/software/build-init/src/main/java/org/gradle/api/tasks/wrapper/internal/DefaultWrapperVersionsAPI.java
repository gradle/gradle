/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks.wrapper.internal;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.gradle.api.GradleException;
import org.gradle.api.resources.MissingResourceException;
import org.gradle.api.resources.TextResourceFactory;
import org.gradle.api.tasks.wrapper.WrapperVersionsResources;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class DefaultWrapperVersionsAPI implements WrapperVersionsResources {
    // order these from most to least stable
    private static final List<String> PLACE_HOLDERS = Arrays.asList(
        "latest",
        "release-candidate",
        "milestone",
        "release-nightly",
        "nightly"
    );

    private final String apiEndpoint;
    private final TextResourceFactory textResourceFactory;

    public DefaultWrapperVersionsAPI(
        String apiPrefix,
        TextResourceFactory textResourceFactory
    ) {
        this.apiEndpoint = apiPrefix;
        this.textResourceFactory = textResourceFactory;
    }

    public static boolean isPlaceHolder(String placeHolder) {
        return PLACE_HOLDERS.contains(placeHolder);
    }

    public String getSingleVersion(String placeHolder) {
        try {
            String target = placeHolder;
            if (placeHolder.equals("latest")) {
                target = "current";
            }
            return getVersion(textResourceFactory.fromUri(String.format(apiEndpoint, target)).asString(), placeHolder);
        } catch (MissingResourceException e) {
            throw new WrapperVersionException("Unable to resolve Gradle version for '" + placeHolder + "'.");
        }
    }

    public List<String> getVersionsList(String placeHolder) {
        try {
            return getVersions(textResourceFactory.fromUri(String.format(apiEndpoint, placeHolder)).asString());
        } catch (MissingResourceException e) {
            throw new WrapperVersionException("Unable to resolve list of Gradle versions for '" + placeHolder + "'.");
        }
    }

    static String getVersion(String json, String placeHolder) {
        Type type = new TypeToken<Map<String, String>>() {}.getType();
        Map<String, String> map = new Gson().fromJson(json, type);
        String version = map.get("version");
        if (version == null) {
            throw new GradleException("There is currently no version information available for '" + placeHolder + "'.");
        }
        return version;
    }

    static List<String> getVersions(String json) {
        Type type = new TypeToken<List<Map<String, String>>>() {}.getType();
        List<Map<String, String>> map = new Gson().fromJson(json, type);
        return map.stream()
            .map(m -> m.get("version"))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * This exception is thrown when the wrapper task is run in an attempt to update the wrapper version and
     * an invalid version is specified.
     */
    public static final class WrapperVersionException extends GradleException implements ResolutionProvider {
        public WrapperVersionException(String message) {
            super(message);
        }

        public WrapperVersionException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }

        @Override
        public List<String> getResolutions() {
            return Arrays.asList(suggestActualVersion(), suggestDynamicVersions());
        }

        private String suggestActualVersion() {
            return "Specify a valid Gradle release listed on https://gradle.org/releases/.";
        }

        private String suggestDynamicVersions() {
            String validStrings = DefaultWrapperVersionsAPI.PLACE_HOLDERS.stream()
                .map(s -> String.format("'%s'", s))
                .collect(Collectors.joining(", "));
            return String.format("Use one of the following dynamic version specifications: %s.", validStrings);
        }
    }
}
