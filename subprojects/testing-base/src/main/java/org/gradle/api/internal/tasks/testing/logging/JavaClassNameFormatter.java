/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.logging;

import javax.annotation.Nonnull;

public class JavaClassNameFormatter {
    private static final char PACKAGE_SEPARATOR = '.';

    public static String abbreviateJavaPackage(@Nonnull String qualifiedClassName, int maxLength) {
        if (qualifiedClassName.length() <= maxLength || qualifiedClassName.indexOf(PACKAGE_SEPARATOR) == -1) {
            return qualifiedClassName;
        }

        final int maxLengthWithoutEllipsis = maxLength - 3;
        int beginIdx = 0;
        // We always want to include className, even if longer than max length
        int endIdx = qualifiedClassName.lastIndexOf(PACKAGE_SEPARATOR);
        int iterations = 0;

        while(beginIdx + (qualifiedClassName.length() - endIdx) <= maxLengthWithoutEllipsis) {
            // Alternate appending packages at beginning and end until we reach max length
            if (iterations % 2 == 0) {
                int tmp = qualifiedClassName.indexOf(PACKAGE_SEPARATOR, beginIdx + 1);
                if (tmp == -1 || tmp + (qualifiedClassName.length() - endIdx) > maxLengthWithoutEllipsis) {
                    break;
                }
                beginIdx = tmp;
            } else {
                int tmp = qualifiedClassName.lastIndexOf(PACKAGE_SEPARATOR, endIdx - 1);
                if (tmp == -1 || beginIdx + (qualifiedClassName.length() - tmp) > maxLengthWithoutEllipsis) {
                    break;
                }
                endIdx = tmp;
            }
            iterations++;
        }

        return qualifiedClassName.substring(0, beginIdx) + "..." + qualifiedClassName.substring(endIdx + 1);
    }
}
