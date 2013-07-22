/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.foundation;

/**
 * Small helper class that aids walking a full task path which can be multiple projects deep with a task on the end.
 */
public class PathParserPortion {
    private String firstPart;
    private String remainder;

    public PathParserPortion(String path) {
        if (path != null && path.length() > 0) {
            if (path.startsWith(":"))    //skip the first character, if its a colon. This is optional and makes it absolute, vs relative.
            {
                path = path.substring(1);
            }

            int indexOfColon = path.indexOf(':');
            if (indexOfColon == -1) {
                firstPart = path;
            } else {
                firstPart = path.substring(0, indexOfColon);  //get everyting up to the colon
                remainder = path.substring(indexOfColon + 1); //everything else
            }
        }
    }

    public String getFirstPart() {
        return firstPart;
    }

    public String getRemainder() {
        return remainder;
    }

    public boolean hasRemainder() {
        return remainder != null;
    }

    public String toString() {
        return firstPart + " -> " + remainder;
    }
}
