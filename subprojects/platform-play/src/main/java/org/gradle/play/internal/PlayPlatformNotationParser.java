/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal;

import org.gradle.api.tasks.Optional;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.MapKey;
import org.gradle.internal.typeconversion.MapNotationConverter;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypeConversionException;
import org.gradle.platform.base.internal.DefaultPlatformRequirement;
import org.gradle.platform.base.internal.PlatformRequirement;

public class PlayPlatformNotationParser {

    private static final NotationParserBuilder<PlatformRequirement> BUILDER = NotationParserBuilder
            .toType(PlatformRequirement.class)
            .fromCharSequence(new StringConverter())
            .converter(new MapConverter());


    public static NotationParser<Object, PlatformRequirement> parser() {
        return builder().toComposite();
    }

    private static NotationParserBuilder<PlatformRequirement> builder() {
        return BUILDER;
    }

    static class MapConverter extends MapNotationConverter<PlatformRequirement> {
        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Map defining the platform versions").example("[play: '" + DefaultPlayPlatform.DEFAULT_PLAY_VERSION + "', scala:'2.11.8', java: '1.6']");
        }

        protected PlatformRequirement parseMap(@MapKey("play") String playVersion,
                                               @MapKey("scala") @Optional String scalaVersion,
                                               @MapKey("java") @Optional String javaVersion) {
            return new PlayPlatformRequirement(playVersion, scalaVersion, javaVersion);
        }
    }

    static class StringConverter implements NotationConverter<String, PlatformRequirement> {
        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("The name of a Play platform").example("'play-" + DefaultPlayPlatform.DEFAULT_PLAY_VERSION + "'.");
        }

        @Override
        public void convert(String notation, NotationConvertResult<? super PlatformRequirement> result) throws TypeConversionException {
            result.converted(new DefaultPlatformRequirement(notation));
        }
    }
}
