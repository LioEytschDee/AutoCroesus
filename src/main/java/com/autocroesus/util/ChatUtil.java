package com.autocroesus.util;

import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;

public class ChatUtil {
   public static void msg(String message) {
      msg(Component.literal(message));
   }

   public static void msg(Component component) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.player.sendSystemMessage(component);
      } else if (mc.gui != null) {
         mc.gui.getChat().addClientSystemMessage(component);
      }
   }
}