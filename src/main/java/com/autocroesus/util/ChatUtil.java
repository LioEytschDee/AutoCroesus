package com.autocroesus.util;

import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

public class ChatUtil {
   public static void msg(String message) {
      msg(Text.literal(message));
   }

   public static void msg(Text component) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         mc.player.sendMessage(component, false);
      } else if (mc.inGameHud != null) {
         mc.inGameHud.getChatHud().addMessage(component);
      }
   }
}
