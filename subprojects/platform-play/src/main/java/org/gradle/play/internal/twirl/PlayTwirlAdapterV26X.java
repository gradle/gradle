/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.play.internal.twirl;

import org.gradle.language.twirl.TwirlImports;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class PlayTwirlAdapterV26X implements VersionedPlayTwirlAdapter {

    // Based on https://github.com/playframework/playframework/blob/2.6.0/framework/src/build-link/src/main/java/play/TemplateImports.java
    private static List<String> defaultTemplateImports = Collections.unmodifiableList(
        Arrays.asList(
            "models._",
            "controllers._",
            "play.api.i18n._",
            "play.api.templates.PlayMagic._"
        ));

    private static final List<String> DEFAULT_JAVA_IMPORTS;
    private static final List<String> DEFAULT_SCALA_IMPORTS;
    static {
        List<String> minimalJavaImports = new ArrayList<String>();
        minimalJavaImports.addAll(defaultTemplateImports);
        minimalJavaImports.add("java.lang._");
        minimalJavaImports.add("java.util._");
        minimalJavaImports.add("scala.collection.JavaConverters._");
        minimalJavaImports.add("play.core.j.PlayMagicForJava._");
        minimalJavaImports.add("play.mvc._");
        minimalJavaImports.add("play.api.data.Field");
        minimalJavaImports.add("play.mvc.Http.Context.Implicit._");

        List<String> defaultJavaImports = new ArrayList<String>();
        defaultJavaImports.addAll(minimalJavaImports);
        defaultJavaImports.add("play.data._");
        defaultJavaImports.add("play.core.j.PlayFormsMagicForJava._");
        DEFAULT_JAVA_IMPORTS = Collections.unmodifiableList(defaultJavaImports);

        List<String> scalaImports = new ArrayList<String>();
        scalaImports.addAll(defaultTemplateImports);
        scalaImports.add("play.api.mvc._");
        scalaImports.add("play.api.data._");
        DEFAULT_SCALA_IMPORTS = Collections.unmodifiableList(scalaImports);
    }

    @Override
    public List<String> getDefaultImports(TwirlImports language) {
        return language == TwirlImports.JAVA ? DEFAULT_JAVA_IMPORTS : DEFAULT_SCALA_IMPORTS;
    }
}
