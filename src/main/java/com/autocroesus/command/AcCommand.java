package com.autocroesus.command;

import com.autocroesus.config.AcDataStore;
import com.autocroesus.feature.CroesusClaimer;
import com.autocroesus.price.PriceFetcher;
import com.autocroesus.util.ChatUtil;
import com.autocroesus.util.ColorUtil;
import com.autocroesus.util.ItemParser;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.AllowCommand;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.text.HoverEvent.ShowText;

public class AcCommand {
   private static final Pattern FLOOR_PATTERN = Pattern.compile("^[FfMm][1-7]$");
   private static final Pattern LOOT_FLOOR_PATTERN = Pattern.compile("^(?:f|floor):([fFmM][1-7])$");
   private static final Pattern LOOT_SCORE_PATTERN = Pattern.compile("^(?:score|s):(\\d+)$");
   private static final Pattern LOOT_LIMIT_PATTERN = Pattern.compile("^(?:l|limit):(\\d+)$");

   public static void register(Object ignored) {
      ClientSendMessageEvents.ALLOW_COMMAND.register((AllowCommand)command -> {
         String cmd = command.trim();
         boolean isAc = cmd.equals("/ac") || cmd.startsWith("/ac ");
         boolean isLong = cmd.equals("/autocroesus") || cmd.startsWith("/autocroesus ");
         if (!isAc && !isLong) {
            return true;
         }

         String[] parts = cmd.split(" ", -1);
         String[] args = Arrays.copyOfRange(parts, 1, parts.length);
         handleCommand(args);
         return false;
      });
   }

   private static void handleCommand(String[] args) {
      if (args.length == 0) {
         printHelp();
      } else {
         switch (args[0].toLowerCase()) {
            case "go":
               cmdGo(false);
               break;
            case "forcego":
               cmdGo(true);
               break;
            case "reset":
               CroesusClaimer.reset();
               ChatUtil.msg("Reset!");
               break;
            case "api":
               cmdApi();
               break;
            case "settings":
            case "config":
            case "s":
            case "c":
               printSettings();
               break;
            case "noclick":
               cmdNoClick();
               break;
            case "delay":
               cmdDelay(args);
               break;
            case "key":
            case "chestkey":
               cmdKey(args.length > 1 ? args[1] : null);
               break;
            case "kismet":
            case "reroll":
               cmdKismet(args.length > 1 ? args[1] : null);
               break;
            case "alwaysbuy":
               cmdAlwaysBuy(args.length > 1 ? args[1] : null);
               break;
            case "worthless":
               cmdWorthless(args.length > 1 ? args[1] : null);
               break;
            case "loot":
               cmdLoot(Arrays.copyOfRange(args, 1, args.length));
               break;
            default:
               printHelp();
         }
      }
   }

   private static void cmdGo(boolean force) {
      if (force) {
         ChatUtil.msg("§aClaiming without updating API.");
         CroesusClaimer.startAutoClaiming();
      } else {
         long sinceUpdate = System.currentTimeMillis() - AcDataStore.config.lastApiUpdate;
         if (sinceUpdate <= 1800000L) {
            CroesusClaimer.startAutoClaiming();
         } else {
            ChatUtil.msg("§ePrices have not been updated in over 30 minutes. Grabbing data...");
            PriceFetcher.updatePrices().thenAccept(warning -> {
               long prevUpdate = AcDataStore.config.lastApiUpdate;
               AcDataStore.config.lastApiUpdate = System.currentTimeMillis();
               AcDataStore.saveConfig();
               if (warning != null && System.currentTimeMillis() - prevUpdate > 3600000L) {
                  ChatUtil.msg(warning);
               }

               ChatUtil.msg("§aSuccessfully grabbed data from API!");
               CroesusClaimer.startAutoClaiming();
            }).exceptionally(e -> {
               Throwable cause = e.getCause() != null ? e.getCause() : e;
               ChatUtil.msg("§c[Error 107] §fFailed to grab data from API: " + cause.getMessage() + " §7DM 22yrs on Discord");
               ChatUtil.msg("§cTo try again, run //ac api");
               return null;
            });
         }
      }
   }

   private static void cmdApi() {
      ChatUtil.msg("§aGrabbing data...");
      PriceFetcher.updatePrices().thenAccept(warning -> {
         long prevUpdate = AcDataStore.config.lastApiUpdate;
         AcDataStore.config.lastApiUpdate = System.currentTimeMillis();
         AcDataStore.saveConfig();
         if (warning != null && System.currentTimeMillis() - prevUpdate > 3600000L) {
            ChatUtil.msg(warning);
         }

         ChatUtil.msg("§aSuccessfully grabbed data from API!");
      }).exceptionally(e -> {
         Throwable cause = e.getCause() != null ? e.getCause() : e;
         ChatUtil.msg("§c[Error 107] §fFailed to grab data from API: " + cause.getMessage() + " §7DM 22yrs on Discord");
         ChatUtil.msg("§cTo try again, run //ac api");
         return null;
      });
   }

   private static void cmdDelay(String[] args) {
      if (args.length < 2) {
         ChatUtil.msg("§cUsage: //ac delay <ms>");
      } else {
         try {
            int ms = Integer.parseInt(args[1]);
            AcDataStore.config.minClickDelay = ms;
            AcDataStore.saveConfig();
            ChatUtil.msg("Min Click Delay is now §6" + ms + "ms");
            if (ms < 150) {
               ChatUtil.msg(
                  "§cWarning: Setting the delay to a low value with low ping will claim chests so quickly that people in chat might notice. Be careful setting this so low."
               );
            }
         } catch (NumberFormatException e) {
            ChatUtil.msg("§cUsage: //ac delay <ms>");
         }
      }
   }

   private static void cmdNoClick() {
      AcDataStore.config.noClick = !AcDataStore.config.noClick;
      AcDataStore.saveConfig();
      ChatUtil.msg("No Click is now set to " + ColorUtil.formattedBool(AcDataStore.config.noClick));
   }

   private static void cmdKey(String arg) {
      if (arg != null) {
         try {
            long profit = Long.parseLong(arg.replace(",", "").replace("_", ""));
            AcDataStore.config.chestKeyMinProfit = profit;
            AcDataStore.saveConfig();
            ChatUtil.msg("Min chest key profit is now " + ColorUtil.formatNumber(profit));
            return;
         } catch (NumberFormatException var3) {
         }
      }

      AcDataStore.config.useChestKeys = !AcDataStore.config.useChestKeys;
      AcDataStore.saveConfig();
      ChatUtil.msg("Use Chest Keys is now " + ColorUtil.formattedBool(AcDataStore.config.useChestKeys));
   }

   private static void cmdKismet(String arg) {
      if (arg == null) {
         AcDataStore.config.useKismets = !AcDataStore.config.useKismets;
         AcDataStore.saveConfig();
         ChatUtil.msg("Use Kismets is now " + ColorUtil.formattedBool(AcDataStore.config.useKismets));
      } else if (FLOOR_PATTERN.matcher(arg).matches()) {
         String floor = arg.toUpperCase();
         if (AcDataStore.config.kismetFloors.contains(floor)) {
            AcDataStore.config.kismetFloors.remove(floor);
            ChatUtil.msg("§fRemoved " + fmtFloor(floor) + "§f from kismet floors");
         } else {
            AcDataStore.config.kismetFloors.add(floor);
            ChatUtil.msg("§fAdded " + fmtFloor(floor) + "§f to kismet floors");
         }

         AcDataStore.saveConfig();
      } else {
         try {
            long profit = Long.parseLong(arg.replace(",", "").replace("_", ""));
            AcDataStore.config.kismetMinProfit = profit;
            AcDataStore.saveConfig();
            ChatUtil.msg("Min kismet profit is now " + ColorUtil.formatNumber(profit));
         } catch (NumberFormatException e) {
            ChatUtil.msg("§cUsage: //ac kismet [toggle|<floor>|<min_profit>]");
         }
      }
   }

   private static void cmdAlwaysBuy(String id) {
      if (id != null) {
         if (id.equalsIgnoreCase("reset")) {
            AcDataStore.alwaysBuy.clear();
            AcDataStore.alwaysBuy.addAll(Arrays.asList(AcDataStore.DEFAULT_ALWAYS_BUY));
            AcDataStore.saveAlwaysBuy();
            ChatUtil.msg("§aResetting the list of items to always buy to their defaults.");
         } else {
            String upper = id.toUpperCase();
            if (AcDataStore.alwaysBuy.contains(upper)) {
               AcDataStore.alwaysBuy.remove(upper);
               AcDataStore.saveAlwaysBuy();
               ChatUtil.msg("§cRemoved §f" + upper + " §cfrom Always Buy list!");
            } else {
               if (!AcDataStore.itemIdExists(upper)) {
                  ChatUtil.msg(
                     "§cWarning: Could not find §f"
                        + upper
                        + " §cin the Skyblock items database. This could be a new item, so it will be added to the list anyway."
                  );
               }

               AcDataStore.alwaysBuy.add(upper);
               AcDataStore.saveAlwaysBuy();
               ChatUtil.msg("§aAdded §f" + upper + " §ato the list of items to always be bought.");
            }
         }
      } else {
         int count = AcDataStore.alwaysBuy.size();
         ChatUtil.msg("§b§lAlways Buy Items §7(" + count + "):");

         for (String entry : AcDataStore.alwaysBuy) {
            ChatUtil.msg("  " + ItemParser.getFormattedNameFromId(entry));
         }
      }
   }

   private static void cmdWorthless(String id) {
      if (id != null) {
         if (id.equalsIgnoreCase("reset")) {
            AcDataStore.worthless.clear();
            AcDataStore.worthless.addAll(Arrays.asList(AcDataStore.DEFAULT_WORTHLESS));
            AcDataStore.saveWorthless();
            ChatUtil.msg("§aResetting the list of worthless items to their defaults.");
         } else {
            String upper = id.toUpperCase();
            if (AcDataStore.worthless.contains(upper)) {
               AcDataStore.worthless.remove(upper);
               AcDataStore.saveWorthless();
               ChatUtil.msg("§cRemoved §f" + upper + " §cfrom Worthless list!");
            } else {
               if (!AcDataStore.itemIdExists(upper)) {
                  ChatUtil.msg(
                     "§cWarning: Could not find §f"
                        + upper
                        + " §cin the Skyblock items database. This could be a new item, so it will be added to the list anyway."
                  );
               }

               AcDataStore.worthless.add(upper);
               AcDataStore.saveWorthless();
               ChatUtil.msg("§aAdded §f" + upper + " §ato the list of worthless items.");
            }
         }
      } else {
         int count = AcDataStore.worthless.size();
         if (count == 0) {
            ChatUtil.msg("§c§lWorthless list is EMPTY!");
            ChatUtil.msg("§cItems that should be zero-valued are being priced at market rate and will inflate profit.");
            ChatUtil.msg("§cRun §f//ac worthless reset §cto restore the default list.");
         } else {
            ChatUtil.msg("§b§lWorthless Items §7(" + count + "):");

            for (String entry : AcDataStore.worthless) {
               ChatUtil.msg("  " + ItemParser.getFormattedNameFromId(entry));
            }
         }
      }
   }

   private static void cmdLoot(String[] args) {
      if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
         ChatUtil.msg("§e//ac loot [floor:<floor>] [limit:<limit>] [score:<score>]");
         ChatUtil.msg("§7Example: §b//ac loot floor:F7 limit:100 score:300");
      } else {
         int minScore = 0;
         String floor = null;
         int limit = Integer.MAX_VALUE;

         for (String seg : args) {
            Matcher fm = LOOT_FLOOR_PATTERN.matcher(seg);
            if (fm.matches()) {
               floor = fm.group(1).toUpperCase();
            } else {
               Matcher sm = LOOT_SCORE_PATTERN.matcher(seg);
               if (sm.matches()) {
                  int s = Integer.parseInt(sm.group(1));
                  if (s < 0 || s > 317) {
                     ChatUtil.msg("§cScore " + s + " not in valid range 0-317");
                     return;
                  }

                  minScore = s;
               } else {
                  Matcher lm = LOOT_LIMIT_PATTERN.matcher(seg);
                  if (lm.matches()) {
                     limit = Integer.parseInt(lm.group(1));
                  }
               }
            }
         }

         if (!Files.exists(AcDataStore.LOOT_LOG_FILE)) {
            ChatUtil.msg("§cNo loot has been logged!");
         } else {
            List<String> lines;
            try {
               lines = Files.readAllLines(AcDataStore.LOOT_LOG_FILE);
            } catch (IOException e) {
               ChatUtil.msg("§c[Error 108] §fFailed to read loot log: " + e.getMessage() + " §7DM 22yrs on Discord");
               return;
            }

            Collections.reverse(lines);
            int dungeons = 0;
            long totalChestCost = 0L;
            Map<String, Integer> loot = new LinkedHashMap<>();

            for (String line : lines) {
               if (!line.isBlank()) {
                  String[] parts = line.split(" ");
                  if (parts.length >= 3) {
                     String runFloor = parts[0];

                     int runScore;
                     long chestCost;
                     try {
                        runScore = Integer.parseInt(parts[1]);
                        chestCost = Long.parseLong(parts[2]);
                     } catch (NumberFormatException e) {
                        continue;
                     }

                     if ((floor == null || runFloor.equals(floor)) && runScore >= minScore) {
                        dungeons++;
                        totalChestCost += chestCost;

                        for (int i = 3; i < parts.length; i++) {
                           String[] kv = parts[i].split(":");
                           if (kv.length == 2) {
                              try {
                                 loot.merge(kv[0], Integer.parseInt(kv[1]), Integer::sum);
                              } catch (NumberFormatException var33) {
                              }
                           }
                        }

                        if (dungeons >= limit) {
                           break;
                        }
                     }
                  }
               }
            }

            if (dungeons == 0) {
               ChatUtil.msg("§cNo runs found matching those filters.");
            } else {
               String floorLabel = floor != null ? fmtFloor(floor) : "§bAll Floors";
               List<long[]> itemsSorted = new ArrayList<>();
               String[] itemIds = loot.keySet().toArray(new String[0]);
               long totalSellPrice = 0L;

               for (int i = 0; i < itemIds.length; i++) {
                  int qty = loot.get(itemIds[i]);
                  Double valueD = AcDataStore.getSellPrice(itemIds[i], true);
                  long value = valueD != null ? (long)valueD.doubleValue() : 0L;
                  long total = value * qty;
                  totalSellPrice += total;
                  itemsSorted.add(new long[]{total, value, qty, i});
               }

               itemsSorted.sort((a, b) -> Long.compare(b[0], a[0]));
               long totalProfit = totalSellPrice - totalChestCost;
               long profitPerRun = dungeons > 0 ? totalProfit / dungeons : 0L;
               StringBuilder hover = new StringBuilder();
               hover.append("§aLoot from §e").append(ColorUtil.formatNumber(dungeons)).append(" §aruns on ").append(floorLabel).append("§a:\n");
               int shown = 0;
               long extraValue = 0L;
               int extraCount = 0;

               for (long[] entry : itemsSorted) {
                  long totalVal = entry[0];
                  long value = entry[1];
                  int qty = (int)entry[2];
                  String id = itemIds[(int)entry[3]];
                  if (shown >= 25) {
                     extraCount++;
                     extraValue += totalVal;
                  } else {
                     shown++;
                     double pct = totalSellPrice > 0L ? totalVal * 100.0 / totalSellPrice : 0.0;
                     hover.append("§b")
                        .append(ColorUtil.formatNumber(qty))
                        .append("x §a")
                        .append(ItemParser.getFormattedNameFromId(id))
                        .append(" §a(§6")
                        .append(ColorUtil.formatNumber(value))
                        .append("§a)")
                        .append(" = §6")
                        .append(ColorUtil.formatNumber(totalVal))
                        .append(" §8(")
                        .append(String.format("%.2f", pct))
                        .append("%)\n");
                  }
               }

               if (extraCount > 0) {
                  hover.append("§a... and ").append(extraCount).append(" more (§6").append(ColorUtil.formatNumber(extraValue)).append("§a)\n");
               }

               hover.append("§cTotal Chest Cost: §6").append(ColorUtil.formatNumber(totalChestCost)).append("\n");
               hover.append("§cTotal Sell Price: §6").append(ColorUtil.formatNumber(totalSellPrice)).append("\n");
               hover.append("§eTotal Profit: §6").append(ColorUtil.formatNumber(totalProfit)).append("\n");
               hover.append("§bProfit/Run: §6").append(ColorUtil.formatNumber(profitPerRun));
               ChatUtil.msg(
                  "§aAverage profit from §e" + ColorUtil.formatNumber(dungeons) + " §aruns on " + floorLabel + "§a: §6" + ColorUtil.formatNumber(profitPerRun)
               );
               Text hoverText = Text.literal(hover.toString());
               MutableText totalLine = Text.literal("§aTotal Profit: §6" + ColorUtil.formatNumber(totalProfit) + " §7(hover for details)");
               totalLine.styled(s -> s.withHoverEvent(new ShowText(hoverText)));
               ChatUtil.msg(totalLine);
            }
         }
      }
   }

   private static String fmtFloor(String floor) {
      if (floor == null) {
         return "§f?";
      } else {
         return floor.startsWith("M") ? "§4§l" + floor : "§a" + floor;
      }
   }

   private static void printHelp() {
      ChatUtil.msg(" ");
      ChatUtil.msg("§b§lAuto Croesus §aCommands");
      ChatUtil.msg("§a//ac §8- §7Show help.");
      ChatUtil.msg("§a//ac go §8- §7Start looting.");
      ChatUtil.msg("§a//ac forcego §8- §7Start without API check.");
      ChatUtil.msg("§a//ac api §8- §7Refresh API.");
      ChatUtil.msg("§a//ac settings §8- §7View settings.");
      ChatUtil.msg("§a//ac delay <ms> §8- §7Set click delay.");
      ChatUtil.msg("");
      ChatUtil.msg("§a//ac kismet §8- §7Toggle rerolls.");
      ChatUtil.msg(
         "§a//ac kismet <min_profit> §8- §7Configure how much profit is required for the chest to not be rerolled. Eg 2,000,000 would mean any chest with >=2m profit will not be rerolled."
      );
      ChatUtil.msg("§a//ac kismet <floor> §8- §7Toggle floor for rerolls.");
      ChatUtil.msg("§a//ac key §8- §7Toggle chest keys.");
      ChatUtil.msg("§a//ac key <min_profit> §8- §7Set min profit for keys.");
      ChatUtil.msg("");
      ChatUtil.msg("§a//ac alwaysbuy [id|reset] §8- §7Manage always-buy items.");
      ChatUtil.msg("§a//ac worthless [id|reset] §8- §7Manage worthless items.");
      ChatUtil.msg("");
      ChatUtil.msg("§a//ac loot §8- §7View loot summary.");
      ChatUtil.msg("§a//ac loot floor:F7 limit:100 score:300 §8- §7Filter loot log.");
      ChatUtil.msg(" ");
   }

   private static void printSettings() {
      String kismetFloors;
      if (AcDataStore.config.kismetFloors.isEmpty()) {
         kismetFloors = "§cNONE";
      } else {
         StringBuilder kfb = new StringBuilder();

         for (String f : AcDataStore.config.kismetFloors) {
            if (kfb.length() > 0) {
               kfb.append("§7, ");
            }

            kfb.append(fmtFloor(f));
         }

         kismetFloors = kfb.toString();
      }

      ChatUtil.msg("§b§lAutoCroesus §aSettings");
      ChatUtil.msg("§7Commands to change settings are shown in brackets.");
      ChatUtil.msg("  Min Click Delay: §6" + AcDataStore.config.minClickDelay + "ms §8(//ac delay <ms>)");
      ChatUtil.msg("  §cWarning: Low values with low ping will make this module ZOOM. Be safe!");
      ChatUtil.msg("");
      ChatUtil.msg("  Use Chest Keys: " + ColorUtil.formattedBool(AcDataStore.config.useChestKeys) + " §8(//ac key)");
      ChatUtil.msg("  Min Chest Key Profit: §6" + ColorUtil.formatNumber(AcDataStore.config.chestKeyMinProfit) + " §8(//ac key <min_profit>)");
      ChatUtil.msg("");
      ChatUtil.msg("  Use Kismets: " + ColorUtil.formattedBool(AcDataStore.config.useKismets) + " §8(//ac kismet)");
      ChatUtil.msg("  Min Kismet Profit: §6" + ColorUtil.formatNumber(AcDataStore.config.kismetMinProfit) + " §8(//ac kismet <min_profit>)");
      ChatUtil.msg("  Kismet Floors: " + kismetFloors + " §8(//ac kismet <floor>)");
   }
}
