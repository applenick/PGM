package tc.oc.pgm.util;

import static net.kyori.adventure.key.Key.key;
import static net.kyori.adventure.sound.Sound.sound;
import static net.kyori.adventure.text.Component.text;

import java.util.Collection;
import java.util.Locale;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.pointer.Pointered;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.renderer.ComponentRenderer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import tc.oc.pgm.util.bukkit.BukkitUtils;
import tc.oc.pgm.util.named.NameStyle;
import tc.oc.pgm.util.text.PlayerComponentProvider;

/** Receiver of chat messages, sounds, titles, and other media. */
@FunctionalInterface
public interface Audience extends ForwardingAudience.Single {

  Sound WARNING_SOUND = sound(key("note.bass"), Sound.Source.MASTER, 1f, 0.75f);
  Component WARNING_MESSAGE = text(" \u26a0 ", NamedTextColor.YELLOW); // ⚠

  static Random random = new Random();
  static String[] RANDOM_SOUNDS = {
    "dig.cloth",
    "dig.glass",
    "liquid.lavapop",
    "note.bass",
    "note.bassattack",
    "note.hat",
    "note.pling",
    "note.snare",
    "random.anvil_break",
    "random.anvil_land",
    "random.anvil_use",
    "random.break",
    "random.chestopen",
    "random.chestclose",
    "random.explode",
    "creeper.primed",
    "random.levelup",
    "random.successful_hit",
    "mob.bat.death",
    "mob.blaze.breathe",
    "mob.blaze.death",
    "mob.blaze.hit",
    "mob.cat.hiss",
    "mob.cat.hitt",
    "mob.cat.meow",
    "mob.cat.purr",
    "mob.cat.purreow",
    "mob.chicken.hurt",
    "mob.cow.hurt",
    "mob.enderdragon.growl",
    "mob.enderdragon.wings",
    "mob.endermen.death",
    "mob.endermen.hit",
    "mob.endermen.scream",
    "mob.ghast.affectionate_scream",
    "mob.ghast.death",
    "mob.ghast.fireball",
    "mob.ghast.moan",
    "mob.ghast.scream",
    "mob.guardian.hit",
    "mob.guardian.flop",
    "mob.horse.angry",
    "mob.horse.death",
    "mob.irongolem.death",
    "mob.pig.death",
    "mob.magmacube.big",
    "mob.villager.death",
    "mob.spider.death",
    "mob.zombie.say",
    "mob.zombie.woodbreak",
    "mob.wolf.bark",
    "mob.wither.death",
    "mob.wither.hurt",
    "mob.wither.shoot",
    "mob.skeleton.death"
  };

  static Sound getRandomSound() {
    String randomKey = RANDOM_SOUNDS[random.nextInt(RANDOM_SOUNDS.length)];
    float randomPitch = random.nextFloat() + 0.5f;
    return sound(key(randomKey), Sound.Source.MASTER, 1f, randomPitch);
  }

  @Override
  default void playSound(final @NotNull Sound sound) {
    this.audience().playSound(getRandomSound());
  }

  default void sendWarning(Component message) {
    sendMessage(WARNING_MESSAGE.append(message.colorIfAbsent(NamedTextColor.RED)));
    playSound(WARNING_SOUND);
  }

  static final String PATTERN = "\\<[@!].*?:[0-" + NameStyle.values().length + "]\\>";

  ComponentRenderer<Pointered> RENDERER =
      new ComponentRenderer<Pointered>() {
        @Override
        public Component render(Component component, final Pointered context) {
          component =
              component.replaceText(
                  TextReplacementConfig.builder()
                      .match(PATTERN)
                      .replacement(
                          (match, b) -> {
                            String input = match.group();
                            String[] parts = input.split(":");
                            if (parts.length == 2) {
                              String id = parts[0].substring(2, parts[0].length());
                              String style = parts[1].substring(0, parts[1].length() - 1);
                              NameStyle ns = NameStyle.values()[Integer.parseInt(style)];
                              return PlayerComponentProvider.render(id, ns, context);
                            }
                            return text("");
                          })
                      .build());

          return GlobalTranslator.render(
              component, context.get(Identity.LOCALE).orElse(Locale.ROOT));
        }
      };

  BukkitAudiences PROVIDER =
      BukkitAudiences.builder(BukkitUtils.getPlugin()).componentRenderer(RENDERER).build();

  static Audience console() {
    return PROVIDER::console;
  }

  static Audience get(CommandSender sender) {
    return () -> PROVIDER.sender(sender);
  }

  static Audience get(Collection<? extends CommandSender> senders) {
    return () -> PROVIDER.filter(senders::contains);
  }

  /** Makes a single audience from a group of audiences */
  static Audience get(Iterable<? extends net.kyori.adventure.audience.Audience> audiences) {
    return () -> net.kyori.adventure.audience.Audience.audience(audiences);
  }

  /** Filter out an audience from a group of audiences */
  static <T extends Audience> Audience filter(Predicate<T> filter, Collection<T> audiences) {
    return get(audiences.stream().filter(filter).collect(Collectors.toList()));
  }

  static Audience empty() {
    return net.kyori.adventure.audience.Audience::empty;
  }
}
