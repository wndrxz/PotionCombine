package dev.wndrxz.potioncombine.sound;

import dev.wndrxz.potioncombine.PotionCombine;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class SoundManager {

    private final PotionCombine plugin;

    public SoundManager(PotionCombine plugin) {
        this.plugin = plugin;
    }

    public void playAt(Location loc, String key, SoundCategory cat, float volume, float pitch) {
        if (loc == null || key == null || key.isBlank()) return;
        World w = loc.getWorld();
        if (w == null) return;
        w.playSound(loc, key, cat, volume, pitch);
    }

    public void playTo(Player who, Location loc, String key, SoundCategory cat, float volume, float pitch) {
        if (who == null || loc == null || key == null || key.isBlank()) return;
        who.playSound(loc, key, cat, volume, pitch);
    }

    public void insert(Player p, Location at) {
        playTo(p, at, plugin.configManager().soundInsert(), SoundCategory.BLOCKS, 0.7f, 1.1f);
    }

    public void reject(Player p, Location at) {
        playTo(p, at, plugin.configManager().soundReject(), SoundCategory.BLOCKS, 0.7f, 1.0f);
    }

    public void retrieve(Player p, Location at) {
        playTo(p, at, plugin.configManager().soundRetrieve(), SoundCategory.BLOCKS, 0.6f, 1.4f);
    }

    public void bubble(Location at) {
        playAt(at, plugin.configManager().soundBubble(), SoundCategory.BLOCKS, 0.5f, 1.0f);
    }

    public void success(Location at) {
        playAt(at, plugin.configManager().soundSuccess(), SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    public void fail(Location at) {
        playAt(at, plugin.configManager().soundFail(), SoundCategory.BLOCKS, 0.9f, 0.8f);
    }

    public void spoil(Location at) {
        playAt(at, plugin.configManager().soundSpoil(), SoundCategory.BLOCKS, 0.7f, 0.9f);
    }

    public void brush(Location at) {
        playAt(at, plugin.configManager().soundBrush(), SoundCategory.BLOCKS, 0.6f, 1.0f);
    }

    public void idle(Location at) {
        playAt(at, plugin.configManager().soundIdle(), SoundCategory.BLOCKS, 0.3f, 0.8f);
    }

    public void cauldronBroken(Location at) {
        playAt(at, plugin.configManager().soundBreak(), SoundCategory.BLOCKS, 0.7f, 0.7f);
    }
}
