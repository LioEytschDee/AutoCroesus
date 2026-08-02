package com.autocroesus.util;

import com.autocroesus.config.AcDataStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ItemParser {
   private static final Set<String> ULTIMATE_ENCHANTS = new HashSet<>(
      Arrays.asList(
         "Bank",
         "Bobbin Time",
         "Chimera",
         "Combo",
         "Duplex",
         "Fatal Tempo",
         "Flash",
         "Habanero Tactics",
         "Inferno",
         "Last Stand",
         "Legion",
         "No Pain No Gain",
         "One For All",
         "Rend",
         "Soul Eater",
         "Swarm",
         "The One",
         "Ultimate Jerry",
         "Ultimate Wise",
         "Wisdom"
      )
   );
   private static final Map<String, String> ITEM_REPLACEMENTS = new HashMap<>();
   private static final Pattern BOOK_PATTERN = Pattern.compile("Enchanted Book \\((?:§.)*([\\w' ]+?) ((?:[IVX]+|\\d+))(?:§.)*\\)");
   private static final Pattern ESSENCE_PATTERN = Pattern.compile("^(\\w+) Essence x(\\d+)$");
   private static final Pattern COST_PATTERN = Pattern.compile("^([\\d,]+) Coins$");
   private static final Pattern PET_LORE_PATTERN = Pattern.compile("^\\[Lvl \\d+\\] (.+)$");
   private static final Map<String, String> COLOR_TO_RARITY = new LinkedHashMap<>();
   private static final String[] ROMAN_NUMS = new String[]{"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
   private static final Pattern ULTIMATE_ENCHANT_PATTERN = Pattern.compile("^ENCHANTMENT_ULTIMATE_([\\w_]+)_(\\d+)$");
   private static final Pattern ENCHANT_PATTERN = Pattern.compile("^ENCHANTMENT_([\\w_]+)_(\\d+)$");
   private static final Map<Character, Integer> ROMAN_VALUES = new HashMap<>();
   private static final Map<String, String> TIER_COLORS = new LinkedHashMap<>();

   public static int decodeRoman(String s) {
      int sum = 0;

      for (int i = 0; i < s.length(); i++) {
         int curr = ROMAN_VALUES.getOrDefault(s.charAt(i), 0);
         int next = i < s.length() - 1 ? ROMAN_VALUES.getOrDefault(s.charAt(i + 1), 0) : 0;
         if (curr < next) {
            sum += next - curr;
            i++;
         } else {
            sum += curr;
         }
      }

      return sum;
   }

   public static String romanNumeral(int n) {
      return n >= 0 && n < ROMAN_NUMS.length ? ROMAN_NUMS[n] : String.valueOf(n);
   }

   private static String[] tryParseBook(String line) {
      Matcher m = BOOK_PATTERN.matcher(line);
      if (!m.find()) {
         return null;
      }

      String bookName = m.group(1).trim();
      String tierStr = m.group(2).trim();
      bookName = ColorUtil.stripColors(bookName).trim();
      boolean isUltimate = ULTIMATE_ENCHANTS.contains(bookName);

      int tier;
      try {
         tier = Integer.parseInt(tierStr);
      } catch (NumberFormatException e) {
         tier = decodeRoman(tierStr);
      }

      String enchantPart = bookName.toUpperCase().replace(" ", "_").replace("'", "");
      String sbId = "ENCHANTMENT_" + (isUltimate ? "ULTIMATE_" : "") + enchantPart + "_" + tier;
      sbId = sbId.replace("ULTIMATE_ULTIMATE_", "ULTIMATE_");
      return new String[]{sbId, "1"};
   }

   private static String[] tryParseEssence(String line) {
      Matcher m = ESSENCE_PATTERN.matcher(line);
      return !m.matches() ? null : new String[]{"ESSENCE_" + m.group(1).toUpperCase(), m.group(2)};
   }

   private static String[] tryParsePet(String rawLine, String cleanLine) {
      Matcher m = PET_LORE_PATTERN.matcher(cleanLine);
      if (!m.matches()) {
         return null;
      }

      String colorCode = null;

      for (int i = 0; i < rawLine.length() - 1; i++) {
         if (rawLine.charAt(i) == 167) {
            char c = rawLine.charAt(i + 1);
            if (c >= '0' && c <= '9' || c >= 'a' && c <= 'f') {
               colorCode = "§" + c;
               break;
            }
         }
      }

      String rarity = colorCode != null ? COLOR_TO_RARITY.get(colorCode) : null;
      if (rarity == null) {
         return null;
      }

      String petName = m.group(1).trim().toUpperCase().replace(" ", "_");
      return new String[]{"PET_" + petName + "_" + rarity, "1"};
   }

   public static String[] parseLine(String line) {
      String[] book = tryParseBook(line);
      if (book != null) {
         return book;
      }

      String clean = ColorUtil.stripColors(line).trim();
      String[] pet = tryParsePet(line, clean);
      if (pet != null) {
         return pet;
      }

      String[] essence = tryParseEssence(clean);
      if (essence != null) {
         return essence;
      }

      if (ITEM_REPLACEMENTS.containsKey(clean)) {
         return new String[]{ITEM_REPLACEMENTS.get(clean), "1"};
      }

      for (AcDataStore.SbItem item : AcDataStore.sbItems) {
         if (item.name.equals(clean) && !item.id.startsWith("STARRED_")) {
            return new String[]{item.id, "1"};
         }
      }

      String hint = AcDataStore.sbItems.isEmpty()
         ? " (SkyBlock items API not loaded — run //ac api)"
         : " (not in book/essence/pet/item list — may be a new or renamed item)";
      return new String[]{"false", "Could not find item ID for \"" + clean + "\"" + hint};
   }

   public static ItemParser.ChestInfo parseRewards(List<String> fullTooltip, String[] errorOut) {
      int costIdx = -1;

      for (int i = 0; i < fullTooltip.size(); i++) {
         if (ColorUtil.stripColors(fullTooltip.get(i)).contains("Cost")) {
            costIdx = i;
            break;
         }
      }

      if (costIdx < 0) {
         if (errorOut != null) {
            errorOut[0] = "Could not find Cost line";
         }

         return null;
      } else if (costIdx + 1 >= fullTooltip.size()) {
         if (errorOut != null) {
            errorOut[0] = "Cost value line missing";
         }

         return null;
      } else {
         String costStr = ColorUtil.stripColors(fullTooltip.get(costIdx + 1)).trim();
         ItemParser.ChestInfo info = new ItemParser.ChestInfo();
         if (!costStr.contains("FREE")) {
            Matcher cm = COST_PATTERN.matcher(costStr);
            if (!cm.matches()) {
               if (errorOut != null) {
                  errorOut[0] = "Could not parse cost: \"" + costStr + "\"";
               }

               return null;
            }

            info.cost = Long.parseLong(cm.group(1).replace(",", ""));
         }

         int lootEnd = costIdx - 1;

         for (int i = 2; i < lootEnd; i++) {
            String line = fullTooltip.get(i);
            String clean = ColorUtil.stripColors(line).trim();
            if (!clean.isEmpty()) {
               String[] result = parseLine(line);
               if (result[0].equals("false")) {
                  if (errorOut != null) {
                     errorOut[0] = result[1];
                  }

                  return null;
               }

               String sbId = result[0];
               int qty = Integer.parseInt(result[1]);
               Double itemValue = AcDataStore.getSellPrice(sbId, true);
               if (itemValue == null) {
                  String priceHint;
                  if (AcDataStore.bzValues.isEmpty() && AcDataStore.binValues.isEmpty()) {
                     priceHint = " (price caches are empty — run //ac api)";
                  } else {
                     priceHint = " (not in Bazaar or BIN — may be a new item, or add it to worthless if it has no value)";
                  }

                  if (errorOut != null) {
                     errorOut[0] = "Could not find value of \"" + clean + "\" [" + sbId + "]" + priceHint;
                  }

                  return null;
               }

               info.value = info.value + itemValue * qty;
               ItemParser.RewardItem ri = new ItemParser.RewardItem();
               ri.id = sbId;
               ri.qty = qty;
               ri.value = itemValue;
               ri.displayName = line.replaceAll("^§5§o", "").trim();
               info.items.add(ri);
            }
         }

         info.items.sort(Comparator.<ItemParser.RewardItem>comparingDouble(a -> a.value * a.qty).reversed());
         info.profit = Math.round(info.value - info.cost);
         return info;
      }
   }

   public static String getFormattedNameFromId(String itemId) {
      if (itemId.startsWith("ENCHANTMENT_ULTIMATE_")) {
         Matcher m = ULTIMATE_ENCHANT_PATTERN.matcher(itemId);
         if (m.matches()) {
            String enchant = toTitleCase(m.group(1).replace("_", " ").toLowerCase());
            if (itemId.startsWith("ENCHANTMENT_ULTIMATE_WISE")) {
               enchant = "Ultimate Wise";
            }

            if (itemId.startsWith("ENCHANTMENT_ULTIMATE_JERRY")) {
               enchant = "Ultimate Jerry";
            }

            return "§aEnchanted Book (§d§l" + enchant + " " + romanNumeral(Integer.parseInt(m.group(2))) + "§a)§r";
         }
      }

      if (itemId.startsWith("ENCHANTMENT_")) {
         Matcher m = ENCHANT_PATTERN.matcher(itemId);
         if (m.matches()) {
            String enchant = toTitleCase(m.group(1).replace("_", " ").toLowerCase());
            int tier = Integer.parseInt(m.group(2));
            String color = tier >= 9 ? "§d" : (tier == 8 ? "§6" : (tier == 7 ? "§5" : (tier == 6 ? "§9" : (tier == 5 ? "§a" : "§f"))));
            return "§aEnchanted Book (" + color + enchant + " " + romanNumeral(tier) + "§a)§r";
         }
      }

      if (itemId.startsWith("ESSENCE_")) {
         String essType = itemId.substring(8);
         return "§d" + toTitleCase(essType.replace("_", " ").toLowerCase()) + " Essence§r";
      }

      AcDataStore.SbItem entry = AcDataStore.getItemApiData(itemId);
      if (entry == null) {
         return itemId;
      }

      String color = TIER_COLORS.getOrDefault(entry.tier, "§f");
      return color + entry.name;
   }

   private static String toTitleCase(String s) {
      StringBuilder sb = new StringBuilder();
      boolean nextUpper = true;

      for (char c : s.toCharArray()) {
         if (c == ' ') {
            sb.append(c);
            nextUpper = true;
         } else if (nextUpper) {
            sb.append(Character.toUpperCase(c));
            nextUpper = false;
         } else {
            sb.append(c);
         }
      }

      return sb.toString();
   }

   static {
      ITEM_REPLACEMENTS.put("Shiny Wither Boots", "WITHER_BOOTS");
      ITEM_REPLACEMENTS.put("Shiny Wither Leggings", "WITHER_LEGGINGS");
      ITEM_REPLACEMENTS.put("Shiny Wither Chestplate", "WITHER_CHESTPLATE");
      ITEM_REPLACEMENTS.put("Shiny Wither Helmet", "WITHER_HELMET");
      ITEM_REPLACEMENTS.put("Shiny Necron's Handle", "NECRON_HANDLE");
      ITEM_REPLACEMENTS.put("Wither Shard", "SHARD_WITHER");
      ITEM_REPLACEMENTS.put("Thorn Shard", "SHARD_THORN");
      ITEM_REPLACEMENTS.put("Apex Dragon Shard", "SHARD_APEX_DRAGON");
      ITEM_REPLACEMENTS.put("Power Dragon Shard", "SHARD_POWER_DRAGON");
      ITEM_REPLACEMENTS.put("Scarf Shard", "SHARD_SCARF");
      ITEM_REPLACEMENTS.put("Necron Dye", "DYE_NECRON");
      ITEM_REPLACEMENTS.put("Livid Dye", "DYE_LIVID");
      COLOR_TO_RARITY.put("§6", "LEGENDARY");
      COLOR_TO_RARITY.put("§d", "MYTHIC");
      COLOR_TO_RARITY.put("§5", "EPIC");
      COLOR_TO_RARITY.put("§9", "RARE");
      COLOR_TO_RARITY.put("§a", "UNCOMMON");
      COLOR_TO_RARITY.put("§f", "COMMON");
      ROMAN_VALUES.put('I', 1);
      ROMAN_VALUES.put('V', 5);
      ROMAN_VALUES.put('X', 10);
      ROMAN_VALUES.put('L', 50);
      ROMAN_VALUES.put('C', 100);
      ROMAN_VALUES.put('D', 500);
      ROMAN_VALUES.put('M', 1000);
      TIER_COLORS.put("COMMON", "§f");
      TIER_COLORS.put("UNCOMMON", "§a");
      TIER_COLORS.put("RARE", "§9");
      TIER_COLORS.put("EPIC", "§5");
      TIER_COLORS.put("LEGENDARY", "§6");
      TIER_COLORS.put("MYTHIC", "§d");
      TIER_COLORS.put("SPECIAL", "§c");
      TIER_COLORS.put("VERY_SPECIAL", "§c");
      TIER_COLORS.put("SUPREME", "§4");
   }

   public static class ChestInfo {
      public long cost;
      public double value;
      public long profit;
      public List<ItemParser.RewardItem> items = new ArrayList<>();
      public int slot;
      public String chestName;
      public String chestColor;
   }

   public static class RewardItem {
      public String id;
      public int qty;
      public double value;
      public String displayName;
   }
}
