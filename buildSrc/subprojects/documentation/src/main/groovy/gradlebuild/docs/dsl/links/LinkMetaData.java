/*
 * Copyright 2020 the original author or authors.
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
package gradlebuild.docs.dsl.links;

import java.io.Serializable;

public class LinkMetaData implements Serializable {
    public enum Style { Javadoc, Dsldoc }

    private final Style style;
    private final String displayName;
    private final String urlFragment;

    public LinkMetaData(Style style, String displayName, String urlFragment) {
        this.style = style;
        this.displayName = displayName;
        this.urlFragment = urlFragment;
    }

    public Style getStyle() {
        return style;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUrlFragment() {
        return urlFragment;
    }
}
