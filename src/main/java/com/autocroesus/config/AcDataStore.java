package com.autocroesus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AcDataStore {
   private static final Logger LOG = LoggerFactory.getLogger("AutoCroesus/DataStore");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   public static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("AutoCroesus");
   public static final Path SETTINGS_FILE = CONFIG_DIR.resolve("settings.json");
   public static final Path ALWAYS_BUY_FILE = CONFIG_DIR.resolve("always_buy.txt");
   public static final Path WORTHLESS_FILE = CONFIG_DIR.resolve("worthless.txt");
   public static final Path BZ_VALUES_FILE = CONFIG_DIR.resolve("bzValues.json");
   public static final Path ITEMS_FILE = CONFIG_DIR.resolve("items.json");
   public static final Path BIN_VALUES_FILE = CONFIG_DIR.resolve("binValues.json");
   public static final Path LOOT_LOG_FILE = CONFIG_DIR.resolve("runLoot.txt");
   public static AcConfig config = new AcConfig();
   public static final Map<String, AcDataStore.BzEntry> bzValues = new HashMap<>();
   public static final List<AcDataStore.SbItem> sbItems = new ArrayList<>();
   public static final Map<String, AcDataStore.SbItem> sbItemsById = new HashMap<>();
   public static final Map<String, Double> binValues = new HashMap<>();
   public static final Set<String> alwaysBuy = new LinkedHashSet<>();
   public static final Set<String> worthless = new LinkedHashSet<>();
   public static final String[] DEFAULT_ALWAYS_BUY = new String[]{
           "NECRON_HANDLE",
           "DARK_CLAYMORE",
           "FIRST_MASTER_STAR",
           "SECOND_MASTER_STAR",
           "THIRD_MASTER_STAR",
           "FOURTH_MASTER_STAR",
           "FIFTH_MASTER_STAR",
           "SHADOW_FURY",
           "SHADOW_WARP_SCROLL",
           "IMPLOSION_SCROLL",
           "WITHER_SHIELD_SCROLL",
           "DYE_LIVID"
   };
   public static final String[] DEFAULT_WORTHLESS = new String[]{
           "DUNGEON_DISC_5",
           "DUNGEON_DISC_4",
           "DUNGEON_DISC_3",
           "DUNGEON_DISC_2",
           "DUNGEON_DISC_1",
           "MAXOR_THE_FISH",
           "STORM_THE_FISH",
           "GOLDOR_THE_FISH",
           "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_1",
           "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_2",
           "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_3",
           "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_4",
           "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_5",
           "ENCHANTMENT_ULTIMATE_COMBO_1",
           "ENCHANTMENT_ULTIMATE_COMBO_2",
           "ENCHANTMENT_ULTIMATE_COMBO_3",
           "ENCHANTMENT_ULTIMATE_COMBO_4",
           "ENCHANTMENT_ULTIMATE_COMBO_5",
           "ENCHANTMENT_ULTIMATE_BANK_1",
           "ENCHANTMENT_ULTIMATE_BANK_2",
           "ENCHANTMENT_ULTIMATE_BANK_3",
           "ENCHANTMENT_ULTIMATE_BANK_4",
           "ENCHANTMENT_ULTIMATE_BANK_5",
           "ENCHANTMENT_ULTIMATE_JERRY_1",
           "ENCHANTMENT_ULTIMATE_JERRY_2",
           "ENCHANTMENT_ULTIMATE_JERRY_3",
           "ENCHANTMENT_ULTIMATE_JERRY_4",
           "ENCHANTMENT_ULTIMATE_JERRY_5",
           "ENCHANTMENT_FEATHER_FALLING_6",
           "ENCHANTMENT_FEATHER_FALLING_7",
           "ENCHANTMENT_FEATHER_FALLING_8",
           "ENCHANTMENT_FEATHER_FALLING_9",
           "ENCHANTMENT_FEATHER_FALLING_10",
           "ENCHANTMENT_INFINITE_QUIVER_6",
           "ENCHANTMENT_INFINITE_QUIVER_7",
           "ENCHANTMENT_INFINITE_QUIVER_8",
           "ENCHANTMENT_INFINITE_QUIVER_9",
           "ENCHANTMENT_INFINITE_QUIVER_10"
   };

   public static void load() {
      try {
         Files.createDirectories(CONFIG_DIR);
      } catch (IOException e) {
         return;
      }

      if (Files.exists(SETTINGS_FILE)) {
         try (Reader r = Files.newBufferedReader(SETTINGS_FILE)) {
            AcConfig loaded = GSON.fromJson(r, AcConfig.class);
            if (loaded != null) {
               config = loaded;
            }
         } catch (Exception e) {
            LOG.warn("[Error 201] Failed to load settings: {}", e.getMessage());
         }
      }

      if (Files.exists(ALWAYS_BUY_FILE)) {
         loadList(ALWAYS_BUY_FILE, alwaysBuy);
      } else {
         alwaysBuy.addAll(Arrays.asList(DEFAULT_ALWAYS_BUY));
         saveList(ALWAYS_BUY_FILE, alwaysBuy);
      }

      if (Files.exists(WORTHLESS_FILE)) {
         loadList(WORTHLESS_FILE, worthless);
      } else {
         worthless.addAll(Arrays.asList(DEFAULT_WORTHLESS));
         saveList(WORTHLESS_FILE, worthless);
      }

      if (Files.exists(BZ_VALUES_FILE)) {
         try {
            JsonObject obj = JsonParser.parseString(Files.readString(BZ_VALUES_FILE)).getAsJsonObject();
            bzValues.clear();

            for (Entry<String, JsonElement> e : obj.entrySet()) {
               JsonObject v = e.getValue().getAsJsonObject();
               AcDataStore.BzEntry entry = new AcDataStore.BzEntry();
               entry.sellOrderValue = v.get("sellOrderValue").getAsDouble();
               entry.instaSellValue = v.get("instaSellValue").getAsDouble();
               bzValues.put(e.getKey(), entry);
            }
         } catch (Exception e) {
            LOG.warn("[Error 202] Failed to load bzValues.json: {}", e.getMessage());
         }
      }

      if (Files.exists(ITEMS_FILE)) {
         try {
            JsonArray arr = JsonParser.parseString(Files.readString(ITEMS_FILE)).getAsJsonArray();
            sbItems.clear();
            sbItemsById.clear();

            for (JsonElement el : arr) {
               JsonObject obj = el.getAsJsonObject();
               AcDataStore.SbItem item = new AcDataStore.SbItem();
               item.id = obj.has("id") ? obj.get("id").getAsString() : "";
               item.name = obj.has("name") ? obj.get("name").getAsString() : "";
               item.tier = obj.has("tier") ? obj.get("tier").getAsString() : "COMMON";
               sbItems.add(item);
               if (!item.id.startsWith("STARRED_")) {
                  sbItemsById.put(item.id, item);
               }
            }
         } catch (Exception e) {
            LOG.warn("[Error 203] Failed to load items.json: {}", e.getMessage());
         }
      }

      if (Files.exists(BIN_VALUES_FILE)) {
         try {
            JsonObject obj = JsonParser.parseString(Files.readString(BIN_VALUES_FILE)).getAsJsonObject();
            binValues.clear();

            for (Entry<String, JsonElement> e : obj.entrySet()) {
               binValues.put(e.getKey(), e.getValue().getAsDouble());
            }
         } catch (Exception e) {
            LOG.warn("[Error 204] Failed to load binValues.json: {}", e.getMessage());
         }
      }
   }

   public static void saveConfig() {
      try (Writer w = Files.newBufferedWriter(SETTINGS_FILE)) {
         GSON.toJson(config, w);
      } catch (IOException e) {
         LOG.warn("[Error 205] Failed to save config: {}", e.getMessage());
      }
   }

   public static void saveAlwaysBuy() {
      saveList(ALWAYS_BUY_FILE, alwaysBuy);
   }

   public static void saveWorthless() {
      saveList(WORTHLESS_FILE, worthless);
   }

   private static void loadList(Path path, Set<String> set) {
      try {
         set.clear();
         String content = Files.readString(path);

         for (String line : content.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) {
               set.add(t);
            }
         }

         if (set.isEmpty() && !content.isBlank()) {
            LOG.warn("[Error 212] {} exists and is non-empty but contained no valid entries — file may be corrupt", path.getFileName());
         }
      } catch (IOException e) {
         LOG.warn("[Error 206] Failed to load list {}: {}", path, e.getMessage());
      }
   }

   private static void saveList(Path path, Set<String> set) {
      try {
         Files.createDirectories(CONFIG_DIR);
         Files.writeString(path, String.join("\n", set));
      } catch (IOException e) {
         LOG.warn("[Error 207] Failed to save list {}: {}", path, e.getMessage());
      }
   }

   public static void appendLootLog(String line) {
      try {
         Files.createDirectories(CONFIG_DIR);
         if (Files.exists(LOOT_LOG_FILE)) {
            Files.writeString(LOOT_LOG_FILE, "\n" + line, StandardOpenOption.APPEND);
         } else {
            Files.writeString(LOOT_LOG_FILE, line);
         }
      } catch (IOException e) {
         LOG.warn("[Error 208] Failed to append loot log: {}", e.getMessage());
      }
   }

   public static void updateBzValues(Map<String, AcDataStore.BzEntry> newValues) {
      bzValues.clear();
      bzValues.putAll(newValues);

      try {
         JsonObject obj = new JsonObject();

         for (Entry<String, AcDataStore.BzEntry> e : newValues.entrySet()) {
            JsonObject v = new JsonObject();
            v.addProperty("sellOrderValue", e.getValue().sellOrderValue);
            v.addProperty("instaSellValue", e.getValue().instaSellValue);
            obj.add(e.getKey(), v);
         }

         Files.createDirectories(CONFIG_DIR);
         Files.writeString(BZ_VALUES_FILE, GSON.toJson(obj));
      } catch (IOException e) {
         LOG.warn("[Error 209] Failed to save bzValues.json: {}", e.getMessage());
      }
   }

   public static void updateSbItems(List<AcDataStore.SbItem> newItems) {
      sbItems.clear();
      sbItems.addAll(newItems);
      sbItemsById.clear();

      for (AcDataStore.SbItem item : newItems) {
         if (!item.id.startsWith("STARRED_")) {
            sbItemsById.put(item.id, item);
         }
      }

      try {
         Files.createDirectories(CONFIG_DIR);
         Files.writeString(ITEMS_FILE, GSON.toJson(newItems));
      } catch (IOException e) {
         LOG.warn("[Error 210] Failed to save items.json: {}", e.getMessage());
      }
   }

   public static void updateBinValues(Map<String, Double> newBins) {
      binValues.clear();
      binValues.putAll(newBins);

      try {
         JsonObject obj = new JsonObject();

         for (Entry<String, Double> e : newBins.entrySet()) {
            obj.addProperty(e.getKey(), e.getValue());
         }

         Files.createDirectories(CONFIG_DIR);
         Files.writeString(BIN_VALUES_FILE, GSON.toJson(obj));
      } catch (IOException e) {
         LOG.warn("[Error 211] Failed to save binValues.json: {}", e.getMessage());
      }
   }

   public static Double getSellPrice(String sbId, boolean useSellOrder) {
      if (worthless.contains(sbId)) {
         return 0.0;
      }

      AcDataStore.BzEntry bz = bzValues.get(sbId);
      return bz != null ? useSellOrder ? bz.sellOrderValue : bz.instaSellValue : binValues.get(sbId);
   }

   public static boolean itemIdMissing(String id) {
      return !sbItemsById.containsKey(id) && !bzValues.containsKey(id);
   }

   public static AcDataStore.SbItem getItemApiData(String id) {
      return sbItemsById.get(id);
   }

   public static class BzEntry {
      public double sellOrderValue;
      public double instaSellValue;
   }

   public static class SbItem {
      public String id;
      public String name;
      public String tier;
   }
}