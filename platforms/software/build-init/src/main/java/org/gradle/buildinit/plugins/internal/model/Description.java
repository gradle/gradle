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

package org.gradle.buildinit.plugins.internal.model;

import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.Language;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework.JUNIT;
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework.JUNIT_JUPITER;
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework.KOTLINTEST;
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework.SCALATEST;
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework.SPOCK;
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework.TESTNG;

public class Description {
    public final static Description JAVA = new Description(
        Language.JAVA,
        JUNIT_JUPITER,
        asList(JUNIT, JUNIT_JUPITER, TESTNG, SPOCK),
        null, null, null
    );

    public final static Description GROOVY = new Description(
        Language.GROOVY,
        SPOCK,
        singletonList(SPOCK),
        "groovy", null, null
    );

    public final static Description SCALA = new Description(
        Language.SCALA,
        SCALATEST,
        singletonList(SCALATEST),
        "scala", null, null
    );

    public final static Description KOTLIN = new Description(
        Language.KOTLIN,
        KOTLINTEST,
        asList(KOTLINTEST, JUNIT_JUPITER),
        "org.jetbrains.kotlin.jvm", "kotlin", "kotlin-jvm"
    );

    private final Language language;
    private final BuildInitTestFramework defaultTestFramework;
    private final Set<BuildInitTestFramework> supportedTestFrameworks;
    @Nullable
    private final String pluginName;
    @Nullable
    private final String pluginVersionProperty;
    @Nullable
    private final String explicitPluginAlias;

    private Description(
        Language language,
        BuildInitTestFramework defaultTestFramework,
        List<BuildInitTestFramework> supportedTestFrameworks,
        @Nullable String pluginName,
        @Nullable String pluginVersionProperty,
        @Nullable String explicitPluginAlias
    ) {
        this.language = language;
        this.defaultTestFramework = defaultTestFramework;
        this.supportedTestFrameworks = new TreeSet<>(supportedTestFrameworks);
        this.pluginName = pluginName;
        this.pluginVersionProperty = pluginVersionProperty;
        this.explicitPluginAlias = explicitPluginAlias;
    }

    public Language getLanguage() {
        return language;
    }

    public BuildInitTestFramework getDefaultTestFramework() {
        return defaultTestFramework;
    }

    public Set<BuildInitTestFramework> getSupportedTestFrameworks() {
        return supportedTestFrameworks;
    }

    public Optional<String> getPluginName() {
        return Optional.ofNullable(pluginName);
    }

    @Nullable
    public String getPluginVersionProperty() {
        return pluginVersionProperty;
    }

    @Nullable
    public String getExplicitPluginAlias() {
        return explicitPluginAlias;
    }
}
