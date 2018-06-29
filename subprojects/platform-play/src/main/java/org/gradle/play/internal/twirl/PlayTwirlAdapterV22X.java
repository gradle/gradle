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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class PlayTwirlAdapterV22X implements VersionedPlayTwirlAdapter {

    // Based on https://github.com/playframework/playframework/blob/2.2.6/framework/src/sbt-plugin/src/main/scala/PlayKeys.scala
    private static final List<String> DEFAULT_JAVA_IMPORTS = Collections.unmodifiableList(Arrays.asList(
            "play.api.templates._",
            "play.api.templates.PlayMagic._",
            "models._",
            "controllers._",
            "java.lang._",
            "java.util._",
            "scala.collection.JavaConversions._",
            "scala.collection.JavaConverters._",
            "play.api.i18n._",
            "play.core.j.PlayMagicForJava._",
            "play.mvc._",
            "play.data._",
            "play.api.data.Field",
            "play.mvc.Http.Context.Implicit._"));

    private static final List<String> DEFAULT_SCALA_IMPORTS = Collections.unmodifiableList(Arrays.asList(
            "play.api.templates._",
            "play.api.templates.PlayMagic._",
            "models._",
            "controllers._",
            "play.api.i18n._",
            "play.api.mvc._",
            "play.api.data._"));

    @Override
    public List<String> getDefaultImports(TwirlImports language) {
        return language == TwirlImports.JAVA ? DEFAULT_JAVA_IMPORTS : DEFAULT_SCALA_IMPORTS;
    }
}
