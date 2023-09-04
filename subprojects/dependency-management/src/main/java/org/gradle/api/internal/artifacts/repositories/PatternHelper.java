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

package org.gradle.api.internal.artifacts.repositories;

import java.util.HashMap;
import java.util.Map;

import static org.apache.ivy.util.StringUtils.isNullOrEmpty;

public class PatternHelper {

    public static final String TYPE_KEY = "type";

    public static final String EXT_KEY = "ext";

    public static final String ARTIFACT_KEY = "artifact";

    public static final String REVISION_KEY = "revision";

    public static final String MODULE_KEY = "module";

    public static final String ORGANISATION_KEY = "organisation";

    public static final String ORGANISATION_KEY2 = "organization";

    public static final String ORGANISATION_PATH_KEY = "orgPath";

    public static String substituteTokens(String pattern, Map<String, String> attributes) {
        Map<String, Object> tokens = new HashMap<>(attributes);
        if (tokens.containsKey(ORGANISATION_KEY) && !tokens.containsKey(ORGANISATION_KEY2)) {
            tokens.put(ORGANISATION_KEY2, tokens.get(ORGANISATION_KEY));
        }
        if (tokens.containsKey(ORGANISATION_KEY)
            && !tokens.containsKey(ORGANISATION_PATH_KEY)) {
            String org = (String) tokens.get(ORGANISATION_KEY);
            tokens.put(ORGANISATION_PATH_KEY, org == null ? "" : org.replace('.', '/'));
        }

        StringBuilder buffer = new StringBuilder();

        StringBuilder optionalPart = null;
        StringBuilder tokenBuffer = null;
        boolean insideOptionalPart = false;
        boolean insideToken = false;
        boolean tokenSeen = false;
        boolean tokenHadValue = false;

        for (char ch : pattern.toCharArray()) {
            int i = pattern.indexOf(ch);
            switch (ch) {
                case '(':
                    if (insideOptionalPart) {
                        throw new IllegalArgumentException(
                            "invalid start of optional part at position " + i + " in pattern "
                                + pattern);
                    }

                    optionalPart = new StringBuilder();
                    insideOptionalPart = true;
                    tokenSeen = false;
                    tokenHadValue = false;
                    break;
                case ')':
                    if (!insideOptionalPart || insideToken) {
                        throw new IllegalArgumentException(
                            "invalid end of optional part at position " + i + " in pattern "
                                + pattern);
                    }

                    if (tokenHadValue) {
                        buffer.append(optionalPart.toString());
                    } else if (!tokenSeen) {
                        buffer.append('(').append(optionalPart.toString()).append(')');
                    }
                    insideOptionalPart = false;
                    break;
                case '[':
                    if (insideToken) {
                        throw new IllegalArgumentException("invalid start of token at position "
                            + i + " in pattern " + pattern);
                    }

                    tokenBuffer = new StringBuilder();
                    insideToken = true;
                    break;
                case ']':
                    if (!insideToken) {
                        throw new IllegalArgumentException("invalid end of token at position " + i
                            + " in pattern " + pattern);
                    }

                    String token = tokenBuffer.toString();
                    Object tokenValue = tokens.get(token);
                    String value = (tokenValue == null) ? null : tokenValue.toString();
                    if (insideOptionalPart) {
                        tokenHadValue = !isNullOrEmpty(value);
                        optionalPart.append(value);
                    } else {
                        if (value == null) { // the token wasn't set, it's kept as is
                            value = "[" + token + "]";
                        }
                        buffer.append(value);
                    }
                    insideToken = false;
                    tokenSeen = true;
                    break;
                default:
                    if (insideToken) {
                        tokenBuffer.append(ch);
                    } else if (insideOptionalPart) {
                        optionalPart.append(ch);
                    } else {
                        buffer.append(ch);
                    }
                    break;
            }
        }

        if (insideToken) {
            throw new IllegalArgumentException("last token hasn't been closed in pattern "
                + pattern);
        }

        if (insideOptionalPart) {
            throw new IllegalArgumentException("optional part hasn't been closed in pattern "
                + pattern);
        }

        return buffer.toString();
    }

    public static String getTokenString(String token) {
        return "[" + token + "]";
    }

}
