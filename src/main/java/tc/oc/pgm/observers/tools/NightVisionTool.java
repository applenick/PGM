package tc.oc.pgm.observers.tools;

import com.google.common.collect.Lists;
import java.util.List;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import tc.oc.component.Component;
import tc.oc.component.render.ComponentRenderers;
import tc.oc.component.types.PersonalizedTranslatable;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.gui.InventoryGUI;
import tc.oc.pgm.observers.ObserverTool;

public class NightVisionTool implements ObserverTool {

  @Override
  public Component getName() {
    return new PersonalizedTranslatable("observer.tools.nightvision");
  }

  @Override
  public ChatColor getColor() {
    return ChatColor.DARK_PURPLE;
  }

  @Override
  public List<String> getLore(MatchPlayer player) {
    Component status =
        new PersonalizedTranslatable(hasNightVision(player) ? "misc.on" : "misc.off")
            .getPersonalizedText()
            .color(hasNightVision(player) ? ChatColor.GREEN : ChatColor.RED);
    Component lore =
        new PersonalizedTranslatable("observer.tools.nightvision.lore", status)
            .getPersonalizedText()
            .color(ChatColor.GRAY);
    return Lists.newArrayList(ComponentRenderers.toLegacyText(lore, player.getBukkit()));
  }

  @Override
  public Material getMaterial(MatchPlayer player) {
    return hasNightVision(player) ? Material.POTION : Material.GLASS_BOTTLE;
  }

  @Override
  public void onInventoryClick(InventoryGUI menu, MatchPlayer player) {
    toggleNightVision(player);
    menu.refreshWindow(player);
  }

  private boolean hasNightVision(MatchPlayer player) {
    return player.getBukkit().hasPotionEffect(PotionEffectType.NIGHT_VISION);
  }

  public void toggleNightVision(MatchPlayer player) {
    if (hasNightVision(player)) {
      player.getBukkit().removePotionEffect(PotionEffectType.NIGHT_VISION);
    } else {
      player
          .getBukkit()
          .addPotionEffect(
              new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, true, false));
    }
  }
}
