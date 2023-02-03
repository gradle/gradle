/*
 * Copyright 2013 the original author or authors.
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

package org.gradle;

import java.util.Locale;

class LocaleHolder {
    private static Locale locale;

    public static Locale set(Locale locale) {
        Locale old = LocaleHolder.locale;
        LocaleHolder.locale = locale;
        return old;
    }
    public static Locale get() {
        return locale;
    }
}