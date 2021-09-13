package tc.oc.pgm.util.translation;

import com.google.common.collect.Maps;
import java.util.Map;
import org.bukkit.entity.Player;

public class Translation {

  private Player sender;
  private String message;
  private Map<String, String> translated;

  public Translation(Player sender, String message) {
    this.sender = sender;
    this.message = message;
    this.translated = Maps.newHashMap();
  }

  public Player getSender() {
    return sender;
  }

  public String getMessage() {
    return message;
  }

  public String getMessage(String language) {
    return translated.getOrDefault(language, message);
  }

  public Map<String, String> getTranslated() {
    return translated;
  }

  public void addTranslated(String language, String translatedMessage) {
    this.translated.put(language, translatedMessage);
  }
}
