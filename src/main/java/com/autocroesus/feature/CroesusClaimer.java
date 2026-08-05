package com.autocroesus.feature;

import com.autocroesus.config.AcDataStore;
import com.autocroesus.util.ChatUtil;
import com.autocroesus.util.ColorUtil;
import com.autocroesus.util.ItemParser;
import com.autocroesus.util.LootLogger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.NonNullList;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.phys.EntityHitResult;

public class CroesusClaimer {
   public static boolean autoClaiming = false;
   public static boolean kismetSweeping = false;
   private static final List<Integer> failedIndexes = new ArrayList<>();
   private static final List<Integer> loggedIndexes = new ArrayList<>();
   private static String claimFloor = null;
   private static int claimPage = 0;
   private static int claimRunSlot = -1;
   private static int claimChestSlot = -1;
   private static boolean claimSkipKismet = false;
   private static boolean waitingForCroesus = false;
   private static boolean waitingForRunToOpen = false;
   private static boolean waitingForChestToOpen = false;
   private static int lastPageOn = -1;
   private static int waitingOnPage = -1;
   private static long pageChangedAt = 0L;
   private static boolean prevWasInCroesus = false;
   private static long croesusEnteredAt = 0L;
   private static boolean tryingToKismet = false;
   private static boolean canKismet = true;
   private static long kismetSlotEmptyAt = 0L;
   private static boolean anyRunsClaimed = false;
   private static boolean needsToLeaveRunGui = false;
   private static boolean worthlessEmptyWarned = false;
   private static final Set<String> skippedKismetFloors = new LinkedHashSet<>();
   private static long lastClick = 0L;
   private static int indexToClick = -1;
   private static long waitFlagSetAt = 0L;
   private static String prevScreenTitle = "";
   private static final int[] CHEST_SLOTS = new int[]{
           10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43
   };
   private static final String KISMET_FEATHER_MODIFIER_TEXT = "Kismet Feather";
   private static final Pattern CHEST_NAME_PATTERN = Pattern.compile("^(Wood|Gold|Diamond|Emerald|Obsidian|Bedrock)$");
   private static final Pattern CHEST_SCREEN_PATTERN = Pattern.compile("^(Wood|Gold|Diamond|Emerald|Obsidian|Bedrock)(?: Chest)?$");
   private static final Pattern RUN_GUI_PATTERN = Pattern.compile("^(?:Master )?Catacombs - .+$");
   private static final Pattern PAGE_NUM_PATTERN = Pattern.compile("Page (\\d+)");
   private static final Pattern FLOOR_PATTERN = Pattern.compile("Floor (\\w+)");
   private static final Map<String, String> CHEST_COLORS = new LinkedHashMap<>();

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(CroesusClaimer::onTick);
   }

   private static void onTick(Minecraft mc) {
      AbstractClientPlayer player = mc.player;
      if (player != null && mc.level != null) {
         tickKillSwitch(mc);
         tickExecuteClick(mc, player);
         tickStartClaiming(mc, player);
         tickCroesusMenu(mc, player);
         tickRunGui(mc, player);
         tickChestScreen(mc, player);
      }
   }

   private static void tickKillSwitch(Minecraft mc) {
      if (autoClaiming || kismetSweeping) {
         if (!InputConstants.isKeyDown(mc.getWindow(), 340)
                 && !InputConstants.isKeyDown(mc.getWindow(), 344)
                 && !InputConstants.isKeyDown(mc.getWindow(), 256)) {
            String title = mc.screen != null ? getScreenTitle(mc) : "";
            if (title.isEmpty() && !prevScreenTitle.isEmpty()) {
               boolean prevWasOurs = prevScreenTitle.contains("Croesus") || RUN_GUI_PATTERN.matcher(prevScreenTitle).matches();
               if (prevWasOurs && !waitingForRunToOpen && !waitingForChestToOpen) {
                  reset();
                  ChatUtil.msg("Kill switch activated!");
               }
            }

            prevScreenTitle = title;
         } else {
            reset();
            ChatUtil.msg("Kill switch activated!");
         }
      }
   }

   private static void tickExecuteClick(Minecraft mc, AbstractClientPlayer player) {
      if (indexToClick >= 0) {
         if (System.currentTimeMillis() - lastClick >= AcDataStore.config.minClickDelay) {
            AbstractContainerMenu menu = player.containerMenu;
            if (menu.getItems().size() > indexToClick) {
               lastClick = System.currentTimeMillis();
               if (AcDataStore.config.noClick) {
                  ChatUtil.msg("§eClick §f" + indexToClick);
               } else if (mc.gameMode != null) {
                  mc.gameMode.handleContainerInput(menu.containerId, indexToClick, 1, ContainerInput.PICKUP, player);
               }

               indexToClick = -1;
            }
         }
      }
   }

   private static void tickStartClaiming(Minecraft mc, AbstractClientPlayer player) {
      if ((autoClaiming || kismetSweeping) && !waitingForCroesus) {
         if (player.containerMenu == player.inventoryMenu) {
            if (waitingOnPage < 0) {
               if (!waitingForRunToOpen && !waitingForChestToOpen) {
                  startClaiming(mc, player);
               } else if (System.currentTimeMillis() - waitFlagSetAt >= 3000L) {
                  ChatUtil.msg("§c[Error 103] §fSequence out of sync, stopping.");
                  reset();
               }
            }
         }
      } else if ((autoClaiming || kismetSweeping) && !inCroesus(mc)) {
         if (System.currentTimeMillis() - waitFlagSetAt >= 3000L) {
            ChatUtil.msg("§c[Error 115] §fFailed to click Croesus, try again.");
            reset();
         }
      }
   }

   private static void tickCroesusMenu(Minecraft mc, AbstractClientPlayer player) {
      boolean nowInCroesus = inCroesus(mc);
      if (nowInCroesus && !prevWasInCroesus) {
         croesusEnteredAt = System.currentTimeMillis();
      }

      prevWasInCroesus = nowInCroesus;
      if (nowInCroesus) {
         if ((autoClaiming || kismetSweeping) && !waitingForRunToOpen) {
            waitingForCroesus = false;
            if (System.currentTimeMillis() - croesusEnteredAt >= 300L) {
               if (claimRunSlot < 0) {
                  resetClaimInfo();
               }

               AbstractContainerMenu menu = player.containerMenu;
               int page = getCurrPage(menu);
               if (waitingOnPage >= 0) {
                  if (page != waitingOnPage) {
                     if (System.currentTimeMillis() - pageChangedAt > 5000L) {
                        ChatUtil.msg("§c[Error 110] §fTimed out waiting for page " + waitingOnPage + " (stuck on page " + page + ").");
                        reset();
                     }

                     return;
                  }

                  waitingOnPage = -1;
               }

               if (claimRunSlot < 0) {
                  int[] slotAndFloor = findUnopenedChest(menu, page);
                  if (slotAndFloor != null) {
                     int slot = slotAndFloor[0];
                     int floorNum = slotAndFloor[1];
                     boolean isMaster = slotAndFloor[2] == 1;
                     String floorKey = (isMaster ? "M" : "F") + floorNum;
                     int extendedIndex = slot + (page - 1) * 54;
                     Boolean kismetStruckThrough = isModifierStruckThrough(getSlot(menu, slot));
                     boolean alreadyUsedKismet = Boolean.TRUE.equals(kismetStruckThrough);

                     if (kismetSweeping && (!AcDataStore.config.kismetFloors.contains(floorKey) || alreadyUsedKismet)) {
                        failedIndexes.add(extendedIndex);
                        return;
                     }

                     claimFloor = floorKey;
                     claimPage = page;
                     claimRunSlot = slot;
                     claimChestSlot = -1;
                     claimSkipKismet = !kismetSweeping && alreadyUsedKismet;
                     waitingForRunToOpen = true;
                     waitFlagSetAt = System.currentTimeMillis();
                     indexToClick = slot;
                  } else {
                     ItemStack nextArrow = getSlot(menu, 53);
                     if (!nextArrow.isEmpty() && ColorUtil.stripColors(nextArrow.getHoverName().getString()).contains("Next Page")) {
                        if (lastPageOn != page) {
                           lastPageOn = page;
                           indexToClick = 53;
                           waitingOnPage = page + 1;
                           pageChangedAt = System.currentTimeMillis();
                        }
                     } else {
                        if (kismetSweeping) {
                           if (!canKismet) {
                              ChatUtil.msg("§eKismet sweep stopped §f— out of Kismet Feathers.");
                           } else {
                              ChatUtil.msg("§aKismet sweep complete!");
                           }
                        } else if (!canKismet) {
                           StringBuilder fb = new StringBuilder();

                           for (String f : skippedKismetFloors) {
                              if (!fb.isEmpty()) {
                                 fb.append("§f, ");
                              }

                              fb.append(fmtFloor(f));
                           }

                           String floors = fb.toString();
                           if (anyRunsClaimed) {
                              ChatUtil.msg("§eSome chests looted! §fKismets needed for: " + floors);
                           } else {
                              ChatUtil.msg(
                                      "§eCould not find kismets. §fAuto claiming for "
                                              + floors
                                              + " §fis disabled until kismetting is turned off or kismets are available to use."
                              );
                           }
                        } else {
                           ChatUtil.msg("§aAll chests looted!");
                        }

                        reset();
                        if (mc.screen != null) {
                           mc.setScreen(null);
                        }
                     }
                  }
               } else if (page == claimPage) {
                  lastPageOn = -1;
                  indexToClick = claimRunSlot;
                  waitingForRunToOpen = true;
                  waitFlagSetAt = System.currentTimeMillis();
               } else if (lastPageOn == page) {
                  if (System.currentTimeMillis() - pageChangedAt > 5000L) {
                     ChatUtil.msg("§c[Error 111] §fTimed out navigating to page " + claimPage + " (stuck on page " + page + ").");
                     reset();
                  }
               } else {
                  lastPageOn = page;
                  indexToClick = 53;
                  pageChangedAt = System.currentTimeMillis();
               }
            }
         }
      }
   }

   private static void tickRunGui(Minecraft mc, AbstractClientPlayer player) {
      if (!autoClaiming && !kismetSweeping && claimRunSlot < 0) {
         resetClaimInfo();
      } else if (!inRunGui(mc)) {
         if (claimRunSlot < 0) {
            resetClaimInfo();
         }
      } else if (isInvLoaded(mc, player)) {
         if (!waitingForChestToOpen) {
            waitingForRunToOpen = false;
            lastPageOn = -1;
            waitingOnPage = -1;
            if (claimChestSlot >= 0) {
               waitingForChestToOpen = true;
               waitFlagSetAt = System.currentTimeMillis();
               indexToClick = claimChestSlot;
               claimChestSlot = -1;
            } else {
               AbstractContainerMenu menu = player.containerMenu;
               if ((autoClaiming || kismetSweeping) && !worthlessEmptyWarned && AcDataStore.worthless.isEmpty()) {
                  worthlessEmptyWarned = true;
                  ChatUtil.msg(
                          "§e[Warning] §fWorthless list is empty! Items that should be zero-valued will count as profit. Run §b//ac worthless reset §fto restore defaults."
                  );
               }

               ArrayList<ItemParser.ChestInfo> chestData = new ArrayList<>();
               int totalChestCount = 0;
               int nonChestItemsPresent = 0;

               for (int i = 0; i < 27; i++) {
                  ItemStack stack = getSlot(menu, i);
                  if (!stack.isEmpty()) {
                     String itemName = ColorUtil.stripColors(stack.getHoverName().getString());
                     Matcher m = CHEST_NAME_PATTERN.matcher(itemName);
                     if (!m.matches()) {
                        nonChestItemsPresent++;
                     } else {
                        totalChestCount++;
                        String chestName = m.group(1);
                        List<String> tooltip = getTooltip(stack);
                        int costIdx = -1;

                        for (int j = 0; j < tooltip.size(); j++) {
                           if (ColorUtil.stripColors(tooltip.get(j)).contains("Cost")) {
                              costIdx = j;
                              break;
                           }
                        }

                        if (costIdx < 0) {
                           if (autoClaiming || kismetSweeping) {
                              ChatUtil.msg("§c[Error 104] §fCould not find loot end.");
                              failedIndexes.add(claimRunSlot + (claimPage - 1) * 54);
                              resetClaimInfo();
                              indexToClick = 30;
                           }

                           return;
                        }

                        String[] errorOut = new String[]{null};
                        ItemParser.ChestInfo info = ItemParser.parseRewards(tooltip, errorOut);
                        if (info == null) {
                           if (autoClaiming || kismetSweeping) {
                              String chestColor = CHEST_COLORS.getOrDefault(chestName, "§f");
                              ChatUtil.msg(
                                      "§c[Error 105] §fFailed to check " + chestColor + chestName + "§r Chest: §r" + errorOut[0]
                              );
                              ChatUtil.msg("§eThis run will be skipped as the info for this chest is incomplete.");
                              failedIndexes.add(claimRunSlot + (claimPage - 1) * 54);
                              resetClaimInfo();
                              indexToClick = 30;
                           }

                           return;
                        }

                        info.slot = i;
                        info.chestName = chestName;
                        info.chestColor = CHEST_COLORS.getOrDefault(chestName, "§f");
                        if (Double.isNaN(info.value) || Double.isInfinite(info.value)) {
                           if (autoClaiming || kismetSweeping) {
                              ChatUtil.msg(
                                      "§c[Error 114] §f"
                                              + info.chestColor
                                              + chestName
                                              + "§r Chest has invalid calculated value ("
                                              + info.value
                                              + ")."
                              );
                              ChatUtil.msg("§7Run §f//ac api §7to refresh prices, then try again.");
                              failedIndexes.add(claimRunSlot + (claimPage - 1) * 54);
                              resetClaimInfo();
                              indexToClick = 30;
                           }

                           return;
                        }

                        chestData.add(info);
                     }
                  }
               }

               sortChestData(chestData);
               if (kismetSweeping && claimRunSlot >= 0) {
                  handleSweepChestData(chestData, nonChestItemsPresent);
               } else if (autoClaiming && claimRunSlot >= 0) {
                  if (chestData.isEmpty()) {
                     if (nonChestItemsPresent > 0) {
                        ChatUtil.msg(
                                "§c[Error 116] §fRun GUI has "
                                        + nonChestItemsPresent
                                        + " item(s) in chest slots but none match a known chest name."
                        );
                        ChatUtil.msg("§7Hypixel may have changed chest names. Run §f//ac reset §7if stuck.");
                     } else {
                        ChatUtil.msg("§c[Error 106] §fNo chest data found, skipping run.");
                     }

                     failedIndexes.add(claimRunSlot + (claimPage - 1) * 54);
                     resetClaimInfo();
                     indexToClick = 30;
                  } else {
                     ItemParser.ChestInfo bedrockChest = chestData.stream().filter(c -> "Bedrock".equals(c.chestName)).findFirst().orElse(null);
                     boolean hasAlwaysBuyItem = bedrockChest != null && bedrockChest.items.stream().anyMatch(item -> AcDataStore.alwaysBuy.contains(item.id));
                     if (!hasAlwaysBuyItem
                             && !claimSkipKismet
                             && canKismet
                             && AcDataStore.config.useKismets
                             && bedrockChest != null
                             && AcDataStore.config.kismetFloors.contains(claimFloor)
                             && bedrockChest.profit < AcDataStore.config.kismetMinProfit) {
                        tryingToKismet = true;
                        indexToClick = bedrockChest.slot;
                        waitingForChestToOpen = true;
                        waitFlagSetAt = System.currentTimeMillis();
                     } else {
                        ItemParser.ChestInfo bestChest = chestData.getFirst();
                        printChestBreakdown("Claiming", bestChest);
                        ArrayList<ItemParser.ChestInfo> chestsToClaim = new ArrayList<>();
                        chestsToClaim.add(bestChest);
                        if (chestData.size() > 1) {
                           ItemParser.ChestInfo second = chestData.get(1);
                           if (AcDataStore.config.useChestKeys && second.profit >= AcDataStore.config.chestKeyMinProfit) {
                              printChestBreakdown("Using chest key on", second);
                              claimChestSlot = second.slot;
                              chestsToClaim.add(second);
                           }
                        }

                        int runIndex;
                        if (!loggedIndexes.contains(runIndex = claimRunSlot + (claimPage - 1) * 54)) {
                           loggedIndexes.add(runIndex);
                           LootLogger.logLoot(claimFloor, chestsToClaim, totalChestCount);
                        }

                        anyRunsClaimed = true;
                        indexToClick = bestChest.slot;
                        waitingForChestToOpen = true;
                        waitFlagSetAt = System.currentTimeMillis();
                        failedIndexes.add(runIndex);
                     }
                  }
               } else {
                  if (needsToLeaveRunGui && inRunGui(mc)) {
                     needsToLeaveRunGui = false;
                     indexToClick = 30;
                  }
               }
            }
         }
      }
   }

   /**
    * Decides what to do with an unopened run's chest data while kismet-sweeping.
    * Sweep mode never opens or claims a chest — it only rerolls a low-profit Bedrock
    * chest (once) and then leaves the run untouched either way.
    */
   private static void handleSweepChestData(List<ItemParser.ChestInfo> chestData, int nonChestItemsPresent) {
      int extendedIndex = claimRunSlot + (claimPage - 1) * 54;
      if (chestData.isEmpty()) {
         if (nonChestItemsPresent > 0) {
            ChatUtil.msg(
                    "§c[Error 116] §fRun GUI has " + nonChestItemsPresent + " item(s) in chest slots but none match a known chest name."
            );
            ChatUtil.msg("§7Hypixel may have changed chest names. Run §f//ac reset §7if stuck.");
         } else {
            ChatUtil.msg("§c[Error 106] §fNo chest data found, skipping run.");
         }

         failedIndexes.add(extendedIndex);
         resetClaimInfo();
         indexToClick = 30;
         return;
      }

      ItemParser.ChestInfo bedrockChest = chestData.stream().filter(c -> "Bedrock".equals(c.chestName)).findFirst().orElse(null);
      if (bedrockChest == null) {
         failedIndexes.add(extendedIndex);
         resetClaimInfo();
         indexToClick = 30;
         return;
      }

      if (claimSkipKismet) {
         resetClaimInfo();
         indexToClick = 30;
         return;
      }

      if (!canKismet) {
         ChatUtil.msg("§eKismet sweep stopped §f— out of Kismet Feathers.");
         failedIndexes.add(extendedIndex);
         reset();
         return;
      }

      boolean hasAlwaysBuyItem = bedrockChest.items.stream().anyMatch(item -> AcDataStore.alwaysBuy.contains(item.id));
      if (hasAlwaysBuyItem || bedrockChest.profit >= AcDataStore.config.kismetMinProfit) {
         failedIndexes.add(extendedIndex);
         resetClaimInfo();
         indexToClick = 30;
         return;
      }

      ChatUtil.msg(fmtFloor(claimFloor) + " §fBedrock worth §c" + ColorUtil.formatNumber(bedrockChest.profit) + " §f— rerolling.");
      failedIndexes.add(extendedIndex);
      tryingToKismet = true;
      indexToClick = bedrockChest.slot;
      waitingForChestToOpen = true;
      waitFlagSetAt = System.currentTimeMillis();
   }

   private static void tickChestScreen(Minecraft mc, AbstractClientPlayer player) {
      if (waitingForChestToOpen) {
         AbstractContainerMenu menu = player.containerMenu;
         if (isInvLoaded(mc, player) && menu.getItems().size() >= 32) {
            String title = getScreenTitle(mc);
            Matcher m = CHEST_SCREEN_PATTERN.matcher(title);
            if (m.matches()) {
               String chestName = m.group(1);
               if (tryingToKismet && "Bedrock".equals(chestName) && !claimSkipKismet && getSlot(menu, 50).isEmpty()) {
                  if (kismetSlotEmptyAt == 0L) {
                     kismetSlotEmptyAt = System.currentTimeMillis();
                  }

                  if (System.currentTimeMillis() - kismetSlotEmptyAt < 500L) {
                     return;
                  }
               } else {
                  kismetSlotEmptyAt = 0L;
               }

               waitingForChestToOpen = false;
               if (tryingToKismet && "Bedrock".equals(chestName) && !claimSkipKismet) {
                  tryingToKismet = false;
                  ItemStack kismetSlot = getSlot(menu, 50);
                  String dbgRawName = kismetSlot.isEmpty() ? "(empty)" : kismetSlot.getHoverName().getString();
                  String dbgStripped = ColorUtil.stripColors(dbgRawName);
                  String dbgLore = getLorePlain(kismetSlot);
                  int dbgMenuSize = menu.getItems().size();
                  boolean noKismet;
                  if (kismetSlot.isEmpty()) {
                     ChatUtil.msg("§c[Error 112] §fKismet slot (50) was empty on arrival.");
                     ChatUtil.msg("§7menuSz=" + dbgMenuSize + " invLoaded=" + isInvLoaded(mc, player));
                     noKismet = true;
                  } else if (!dbgStripped.equals("Reroll Chest")) {
                     String loreTrunc = dbgLore.length() > 80 ? dbgLore.substring(0, 80) + "..." : dbgLore;
                     ChatUtil.msg("§c[Error 113] §fKismet slot (50) had an unexpected item name.");
                     ChatUtil.msg("§7name=\"" + dbgStripped + "\" raw=\"" + dbgRawName + "\" menuSz=" + dbgMenuSize);
                     ChatUtil.msg("§7lore: \"" + loreTrunc + "\"");
                     noKismet = true;
                  } else {
                     noKismet = dbgLore.contains("Bring a Kismet Feather");
                  }

                  if (noKismet) {
                     canKismet = false;
                     skippedKismetFloors.add(claimFloor);
                     failedIndexes.add(claimRunSlot + (claimPage - 1) * 54);
                     resetClaimInfo();
                     needsToLeaveRunGui = true;
                     indexToClick = 49;
                     waitingForRunToOpen = true;
                     waitFlagSetAt = System.currentTimeMillis();
                  } else {
                     claimSkipKismet = true;
                     indexToClick = 50;
                  }
               } else {
                  indexToClick = 31;
                  if (claimChestSlot < 0) {
                     resetClaimInfo();
                  }
               }
            }
         }
      }
   }

   public static void startAutoClaiming() {
      reset();
      autoClaiming = true;
      ChatUtil.msg("§aStarting auto claim!");
   }

   public static void startKismetSweep() {
      reset();
      kismetSweeping = true;
      ChatUtil.msg("§aStarting kismet sweep!");
   }

   public static void reset() {
      autoClaiming = false;
      kismetSweeping = false;
      failedIndexes.clear();
      resetClaimInfo();
      waitingForCroesus = false;
      waitingForRunToOpen = false;
      waitingForChestToOpen = false;
      lastPageOn = -1;
      waitingOnPage = -1;
      pageChangedAt = 0L;
      indexToClick = -1;
      tryingToKismet = false;
      canKismet = true;
      kismetSlotEmptyAt = 0L;
      worthlessEmptyWarned = false;
      anyRunsClaimed = false;
      needsToLeaveRunGui = false;
      skippedKismetFloors.clear();
      waitFlagSetAt = 0L;
      prevScreenTitle = "";
      prevWasInCroesus = false;
      croesusEnteredAt = 0L;
   }

   private static void resetClaimInfo() {
      claimFloor = null;
      claimPage = 0;
      claimRunSlot = -1;
      claimChestSlot = -1;
      claimSkipKismet = false;
   }

   private static void startClaiming(Minecraft mc, AbstractClientPlayer player) {
      if (!tryClickCroesus(mc, player)) {
         ChatUtil.msg("§c[Error 101] §fCould not find or reach Croesus.");
         reset();
      } else {
         waitingForCroesus = true;
         waitFlagSetAt = System.currentTimeMillis();
      }
   }

   private static boolean tryClickCroesus(Minecraft mc, AbstractClientPlayer player) {
      ClientLevel level = mc.level;
      if (level == null) {
         return false;
      }

      AABB box = new AABB(
              player.getX() - 5.0,
              player.getY() - 3.0,
              player.getZ() - 5.0,
              player.getX() + 5.0,
              player.getY() + 3.0,
              player.getZ() + 5.0
      );
      List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, box, stand -> ColorUtil.stripColors(stand.getName().getString()).equals("Croesus"));
      if (stands.isEmpty()) {
         return false;
      }

      ArmorStand displayStand = stands.getFirst();
      List<AbstractClientPlayer> npcs = level.getEntitiesOfClass(
              AbstractClientPlayer.class,
              box,
              p -> p != player
                      && p.getUUID().version() == 2
                      && Math.abs(p.getX() - displayStand.getX()) < 0.01
                      && Math.abs(p.getZ() - displayStand.getZ()) < 0.01
      );
      if (npcs.isEmpty()) {
         return false;
      }

      if (npcs.size() > 1) {
         ChatUtil.msg("§c[Error 102] §fFound multiple possible Croesus entities.");
         return false;
      }

      AbstractClientPlayer croesus = npcs.getFirst();
      double distSq = player.distanceToSqr(croesus);
      if (distSq > 16.0) {
         ChatUtil.msg("§c[Error 101] §fCroesus is too far away!");
         return false;
      }

      if (mc.gameMode != null) {
         mc.gameMode.interact(player, croesus, new EntityHitResult(croesus), InteractionHand.MAIN_HAND);
      }

      return true;
   }

   private static String getScreenTitle(Minecraft mc) {
      return mc.screen == null ? "" : ColorUtil.stripColors(mc.screen.getTitle().getString());
   }

   private static boolean inCroesus(Minecraft mc) {
      return getScreenTitle(mc).contains("Croesus");
   }

   private static boolean inRunGui(Minecraft mc) {
      return RUN_GUI_PATTERN.matcher(getScreenTitle(mc)).matches();
   }

   private static boolean isInvLoaded(Minecraft mc, AbstractClientPlayer player) {
      if (mc.screen == null) {
         return false;
      }

      AbstractContainerMenu menu = player.containerMenu;
      if (menu == player.inventoryMenu) {
         return false;
      }

      NonNullList<ItemStack> items = menu.getItems();
      return items.size() > 45 && !items.get(items.size() - 45).isEmpty();
   }

   private static int getCurrPage(AbstractContainerMenu menu) {
      ItemStack next = getSlot(menu, 53);
      ItemStack prev = getSlot(menu, 45);
      String nextName = ColorUtil.stripColors(next.getHoverName().getString());
      if (nextName.contains("Next Page")) {
         for (String line : getLoreLines(next)) {
            Matcher m = PAGE_NUM_PATTERN.matcher(ColorUtil.stripColors(line));
            if (m.find()) {
               return Integer.parseInt(m.group(1)) - 1;
            }
         }
      }

      if (ColorUtil.stripColors(prev.getHoverName().getString()).contains("Previous Page")) {
         for (String line : getLoreLines(prev)) {
            Matcher m = PAGE_NUM_PATTERN.matcher(ColorUtil.stripColors(line));
            if (m.find()) {
               return Integer.parseInt(m.group(1)) + 1;
            }
         }
      }

      return 1;
   }

   private static int[] findUnopenedChest(AbstractContainerMenu menu, int page) {
      for (int slotIdx : CHEST_SLOTS) {
         int extendedIndex = slotIdx + (page - 1) * 54;
         if (!failedIndexes.contains(extendedIndex)) {
            ItemStack stack = getSlot(menu, slotIdx);
            if (stack.isEmpty()) {
               return null;
            }

            if (stack.is(Items.PLAYER_HEAD) && getLorePlain(stack).contains("No chests opened yet!")) {
               String dungeonType = ColorUtil.stripColors(stack.getHoverName().getString());
               boolean isMaster = dungeonType.contains("Master Mode");
               List<String> loreLines = getLoreLines(stack);
               if (!loreLines.isEmpty()) {
                  String floorLine = ColorUtil.stripColors(loreLines.getFirst());
                  Matcher fm = FLOOR_PATTERN.matcher(floorLine);
                  if (!fm.find()) {
                     failedIndexes.add(extendedIndex);
                  } else {
                     String floorStr = fm.group(1);

                     int floorNum;
                     try {
                        floorNum = Integer.parseInt(floorStr);
                     } catch (NumberFormatException e) {
                        floorNum = ItemParser.decodeRoman(floorStr);
                     }

                     String floorKey = (isMaster ? "M" : "F") + floorNum;
                     if (canKismet || !AcDataStore.config.kismetFloors.contains(floorKey)) {
                        return new int[]{slotIdx, floorNum, isMaster ? 1 : 0};
                     }

                     skippedKismetFloors.add(floorKey);
                     failedIndexes.add(extendedIndex);
                  }
               }
            }
         }
      }

      return null;
   }

   private static void sortChestData(List<ItemParser.ChestInfo> chestData) {
      chestData.sort((a, b) -> {
         boolean aHasAlways = a.items.stream().anyMatch(i -> AcDataStore.alwaysBuy.contains(i.id));
         boolean bHasAlways = b.items.stream().anyMatch(i -> AcDataStore.alwaysBuy.contains(i.id));
         if (bHasAlways && !aHasAlways) {
            return 1;
         } else {
            return aHasAlways && !bHasAlways ? -1 : Long.compare(b.profit, a.profit);
         }
      });
   }

   private static String fmtFloor(String floor) {
      if (floor == null) {
         return "§f?";
      } else {
         return floor.startsWith("M") ? "§4§l" + floor : "§a" + floor;
      }
   }

   private static ItemStack getSlot(AbstractContainerMenu menu, int index) {
      return index >= 0 && index < menu.slots.size() ? menu.slots.get(index).getItem() : ItemStack.EMPTY;
   }

   private static List<String> getLoreLines(ItemStack stack) {
      ItemLore lore = stack.get(DataComponents.LORE);
      if (lore == null) {
         return Collections.emptyList();
      }

      ArrayList<String> lines = new ArrayList<>();

      for (Component c : lore.lines()) {
         lines.add(c.getString());
      }

      return lines;
   }

   private static String getLorePlain(ItemStack stack) {
      ItemLore lore = stack.get(DataComponents.LORE);
      if (lore == null) {
         return "";
      }

      StringBuilder sb = new StringBuilder();

      for (Component c : lore.lines()) {
         sb.append(ColorUtil.stripColors(c.getString())).append(' ');
      }

      return sb.toString();
   }

   /**
    * Searches an item's lore for a "Kismet Feather" text run and reports whether that
    * specific run has the strikethrough style applied.
    * Unlike getLoreLines/getLorePlain, this walks the Component tree instead of
    * flattening to a plain string, because strikethrough lives on Style, not on the
    * text content, and Component.getString() discards Style entirely.
    *
    * @return Boolean.TRUE if found and struck through, Boolean.FALSE if found and not
    *         struck through, or null if the Kismet Feather modifier does not appear
    *         in the lore at all.
    */
   private static Boolean isModifierStruckThrough(ItemStack stack) {
      ItemLore lore = stack.get(DataComponents.LORE);
      if (lore == null) {
         return null;
      }

      for (Component line : lore.lines()) {
         Boolean found = findModifierStrikethrough(line, KISMET_FEATHER_MODIFIER_TEXT);
         if (found != null) {
            return found;
         }
      }

      return null;
   }

   private static Boolean findModifierStrikethrough(Component component, String modifierText) {
      if (component.getContents() instanceof PlainTextContents plain && modifierText.equals(plain.text())) {
         return component.getStyle().isStrikethrough();
      }

      for (Component sibling : component.getSiblings()) {
         Boolean found = findModifierStrikethrough(sibling, modifierText);
         if (found != null) {
            return found;
         }
      }

      return null;
   }

   private static void printChestBreakdown(String action, ItemParser.ChestInfo info) {
      ChatUtil.msg(action + " the " + info.chestColor + info.chestName + " Chest §7(profit: §f" + ColorUtil.formatNumber(info.profit) + "§7)");
   }

   private static List<String> getTooltip(ItemStack stack) {
      ArrayList<String> tooltip = new ArrayList<>();
      tooltip.add(stack.getHoverName().getString());
      ItemLore lore = stack.get(DataComponents.LORE);
      if (lore != null) {
         for (Component c : lore.lines()) {
            tooltip.add(c.getString());
         }
      }

      return tooltip;
   }

   static {
      CHEST_COLORS.put("Wood", "§f");
      CHEST_COLORS.put("Gold", "§6");
      CHEST_COLORS.put("Diamond", "§b");
      CHEST_COLORS.put("Emerald", "§2");
      CHEST_COLORS.put("Obsidian", "§5");
      CHEST_COLORS.put("Bedrock", "§8");
   }
}