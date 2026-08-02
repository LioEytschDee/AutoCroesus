package com.autocroesus;

import com.autocroesus.command.AcCommand;
import com.autocroesus.config.AcDataStore;
import com.autocroesus.feature.CroesusClaimer;
import net.fabricmc.api.ClientModInitializer;

public class AutoCroesus implements ClientModInitializer {
   public void onInitializeClient() {
      AcDataStore.load();
      CroesusClaimer.register();
      AcCommand.register(null);
   }
}
