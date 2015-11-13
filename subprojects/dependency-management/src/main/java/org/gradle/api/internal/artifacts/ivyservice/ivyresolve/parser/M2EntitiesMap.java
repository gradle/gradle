/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import java.util.HashMap;

// Used in PomDomParser.decorateWithM2EntityReplacement
class M2EntitiesMap extends HashMap<String, String> {
    public M2EntitiesMap() {
        this.addEntities();
    }

    // generated from ivy's org/apache/ivy/plugins/parser/m2/m2-entities.ent
    private void addEntities() {
        addEntity("nbsp", 160);
        addEntity("iexcl", 161);
        addEntity("cent", 162);
        addEntity("pound", 163);
        addEntity("curren", 164);
        addEntity("yen", 165);
        addEntity("brvbar", 166);
        addEntity("sect", 167);
        addEntity("uml", 168);
        addEntity("copy", 169);
        addEntity("ordf", 170);
        addEntity("laquo", 171);
        addEntity("not", 172);
        addEntity("shy", 173);
        addEntity("reg", 174);
        addEntity("macr", 175);
        addEntity("deg", 176);
        addEntity("plusmn", 177);
        addEntity("sup2", 178);
        addEntity("sup3", 179);
        addEntity("acute", 180);
        addEntity("micro", 181);
        addEntity("para", 182);
        addEntity("middot", 183);
        addEntity("cedil", 184);
        addEntity("sup1", 185);
        addEntity("ordm", 186);
        addEntity("raquo", 187);
        addEntity("frac14", 188);
        addEntity("frac12", 189);
        addEntity("frac34", 190);
        addEntity("iquest", 191);
        addEntity("Agrave", 192);
        addEntity("Aacute", 193);
        addEntity("Acirc", 194);
        addEntity("Atilde", 195);
        addEntity("Auml", 196);
        addEntity("Aring", 197);
        addEntity("AElig", 198);
        addEntity("Ccedil", 199);
        addEntity("Egrave", 200);
        addEntity("Eacute", 201);
        addEntity("Ecirc", 202);
        addEntity("Euml", 203);
        addEntity("Igrave", 204);
        addEntity("Iacute", 205);
        addEntity("Icirc", 206);
        addEntity("Iuml", 207);
        addEntity("ETH", 208);
        addEntity("Ntilde", 209);
        addEntity("Ograve", 210);
        addEntity("Oacute", 211);
        addEntity("Ocirc", 212);
        addEntity("Otilde", 213);
        addEntity("Ouml", 214);
        addEntity("times", 215);
        addEntity("Oslash", 216);
        addEntity("Ugrave", 217);
        addEntity("Uacute", 218);
        addEntity("Ucirc", 219);
        addEntity("Uuml", 220);
        addEntity("Yacute", 221);
        addEntity("THORN", 222);
        addEntity("szlig", 223);
        addEntity("agrave", 224);
        addEntity("aacute", 225);
        addEntity("acirc", 226);
        addEntity("atilde", 227);
        addEntity("auml", 228);
        addEntity("aring", 229);
        addEntity("aelig", 230);
        addEntity("ccedil", 231);
        addEntity("egrave", 232);
        addEntity("eacute", 233);
        addEntity("ecirc", 234);
        addEntity("euml", 235);
        addEntity("igrave", 236);
        addEntity("iacute", 237);
        addEntity("icirc", 238);
        addEntity("iuml", 239);
        addEntity("eth", 240);
        addEntity("ntilde", 241);
        addEntity("ograve", 242);
        addEntity("oacute", 243);
        addEntity("ocirc", 244);
        addEntity("otilde", 245);
        addEntity("ouml", 246);
        addEntity("divide", 247);
        addEntity("oslash", 248);
        addEntity("ugrave", 249);
        addEntity("uacute", 250);
        addEntity("ucirc", 251);
        addEntity("uuml", 252);
        addEntity("yacute", 253);
        addEntity("thorn", 254);
        addEntity("yuml", 255);
    }

    private void addEntity(String name, int charValue) {
        put(name, new String(new char[]{(char) charValue}));
    }
}
