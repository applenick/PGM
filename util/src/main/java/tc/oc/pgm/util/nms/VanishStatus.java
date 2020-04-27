package tc.oc.pgm.util.nms;

import javax.annotation.Nullable;
import org.bukkit.entity.Entity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import tc.oc.pgm.util.bukkit.BukkitUtils;

/** Similar to {@link DeathOverride}, this allows for formatting vanished players names. */
public interface VanishStatus {

  String METADATA_KEY = "isVanished";

  /**
   * Set or clear a metadata flag on the given entity that determines if they are vanished or not.
   */
  static void setVanished(Entity player, @Nullable Boolean vanished) {
    if (vanished != null) {
      player.setMetadata(METADATA_KEY, new FixedMetadataValue(BukkitUtils.getPlugin(), vanished));
    } else {
      player.removeMetadata(METADATA_KEY, BukkitUtils.getPlugin());
    }
  }

  /**
   * Test if the given entity is vanished, first by checking their metadata for an overridden value,
   * and falling back to false as a worst case.
   */
  static boolean isVanished(Entity player) {
    MetadataValue value = player.getMetadata(METADATA_KEY, BukkitUtils.getPlugin());
    if (value != null) {
      return value.asBoolean();
    } else {
      return false;
    }
  }
}
