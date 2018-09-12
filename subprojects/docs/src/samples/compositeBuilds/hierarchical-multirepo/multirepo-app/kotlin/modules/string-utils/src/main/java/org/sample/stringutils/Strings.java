package org.sample.stringutils;

import org.apache.commons.lang3.StringUtils;

public class Strings {
   public static String concat(Object left, Object right) {
     return strip(left) + " " + strip(right);
   }

   private static String strip(Object val) {
     return StringUtils.strip(String.valueOf(val));
   }
}
