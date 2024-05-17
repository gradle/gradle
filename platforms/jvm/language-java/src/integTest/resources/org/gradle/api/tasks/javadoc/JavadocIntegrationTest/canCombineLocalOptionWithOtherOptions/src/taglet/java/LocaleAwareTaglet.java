/*
 * Copyright 2012 the original author or authors.
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
import com.sun.javadoc.Tag;
import com.sun.tools.doclets.Taglet;

import java.util.Locale;
import java.util.Map;

public class LocaleAwareTaglet implements Taglet {
    public boolean inField() {
        return false;
    }

    public boolean inConstructor() {
        return false;
    }

    public boolean inMethod() {
        return false;
    }

    public boolean inOverview() {
        return true;
    }

    public boolean inPackage() {
        return false;
    }

    public boolean inType() {
        return true;
    }

    public boolean isInlineTag() {
        return false;
    }

    public String getName() {
        return "LOCALE_AWARE";
    }

    public String toString(Tag tag) {
        return "<B>USED LOCALE=" + Locale.getDefault() + "</B>\n";
    }

    public String toString(Tag[] tags) {
        return toString(tags[0]);
    }

    public static void register(Map tagletMap) {
        LocaleAwareTaglet taglet = new LocaleAwareTaglet();
        tagletMap.put(taglet.getName(), taglet);
    }
}
