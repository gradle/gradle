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

package org.gradlebuild;

import java.io.File;
import java.util.List;

@SuppressWarnings({"unused", "checkstyle:LeftCurly"})
public class AllowedMethodTypesClass {
    public void validMethod(String arg1, String arg2) {

    }

    public File invalidReturnType(String arg1, String arg2) {
        return null;
    }

    public void invalidParameterType(String arg1, File arg2) {

    }

    public <T extends File> void invalidTypeParameterBoundType(String arg1, String arg2) {

    }

    public List<? extends File> invalidTypeParameterInReturnType(String arg1, String arg2) {
        return null;
    }

    public String[] validArrayTypeParameterInReturnType(String arg1, String arg2) {
        return null;
    }

    public File[] invalidArrayTypeParameterInReturnType(String arg1, String arg2) {
        return null;
    }

    public void invalidTypeParameterInParameterType(String arg1, List<File> arg2) {

    }
}
