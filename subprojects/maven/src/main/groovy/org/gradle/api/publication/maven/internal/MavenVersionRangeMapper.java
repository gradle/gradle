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

package org.gradle.api.publication.maven.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenVersionRangeMapper implements VersionRangeMapper{

    private static final String FIXED_PREFIX = "([\\d\\.]*)";
    private static final String DYN_VERSION_NUMBER = "(\\d+)";
    public static final String PLUS_OPER = "[\\.]?\\+";
    private static final String PLUS_NOTATION_PATTERN = FIXED_PREFIX + DYN_VERSION_NUMBER + PLUS_OPER;

    public final Pattern plusNotationPattern = Pattern.compile(PLUS_NOTATION_PATTERN);

    public String map(String version) {
        Matcher plusNotationMatcher = plusNotationPattern.matcher(version);
        if(plusNotationMatcher.matches()){
            String prefix = plusNotationMatcher.group(1);
            int dynVersionPart = Integer.parseInt(plusNotationMatcher.group(2));
            if(prefix!=null){
                return String.format("[%s%s,%s%s)", prefix, dynVersionPart, prefix, dynVersionPart+1);
            } else{
                return String.format("[%s,%s)", dynVersionPart, dynVersionPart+1);
            }
        }
        return version;
    }
}
