package com.autocroesus.util;

import java.util.regex.Pattern;

public class ColorUtil {
   private static final Pattern COLOR_CODE = Pattern.compile("§[0-9a-fk-orA-FK-OR]");

   public static String stripColors(String s) {
      return s == null ? "" : COLOR_CODE.matcher(s).replaceAll("");
   }

   public static String formatNumber(long num) {
      return String.format("%,d", num);
   }

   public static String formatNumber(double num) {
      return String.format("%,.0f", num);
   }

   public static String formattedBool(boolean b) {
      return b ? "§atrue" : "§cfalse";
   }
}
