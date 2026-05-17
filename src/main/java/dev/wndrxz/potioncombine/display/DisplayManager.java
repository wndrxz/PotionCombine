package dev.wndrxz.potioncombine.display;

import dev.wndrxz.potioncombine.PotionCombine;
import dev.wndrxz.potioncombine.util.Keys;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

public final class DisplayManager {

    private final PotionCombine plugin;

    public DisplayManager(PotionCombine plugin) {
        this.plugin = plugin;
    }

    public UUID spawnText(Location at, Component text) {
        World w = at.getWorld();
        if (w == null) return null;
        TextDisplay td = w.spawn(at, TextDisplay.class, e -> {
            e.text(text);
            e.setBillboard(Display.Billboard.CENTER);
            e.setSeeThrough(true);
            e.setShadowed(false);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            tagOwned(e);
        });
        return td.getUniqueId();
    }

    public void updateText(UUID id, World world, Component text) {
        if (id == null || world == null) return;
        Entity e = world.getEntity(id);
        if (e instanceof TextDisplay td && td.isValid()) td.text(text);
    }

    public UUID spawnItem(Location at, ItemStack item) {
        World w = at.getWorld();
        if (w == null) return null;
        ItemDisplay disp = w.spawn(at, ItemDisplay.class, e -> {
            e.setItemStack(item);
            e.setBillboard(Display.Billboard.FIXED);
            e.setGlowing(true);
            e.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(),
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new Quaternionf()));
            tagOwned(e);
        });
        return disp.getUniqueId();
    }

    public void rotateItem(UUID id, World world, double yawRadians) {
        if (id == null || world == null) return;
        Entity e = world.getEntity(id);
        if (!(e instanceof ItemDisplay disp) || !disp.isValid()) return;
        Transformation t = disp.getTransformation();
        Quaternionf left = new Quaternionf(new AxisAngle4f((float) yawRadians, 0f, 1f, 0f));
        disp.setTransformation(new Transformation(
                t.getTranslation(),
                left,
                t.getScale(),
                t.getRightRotation()));
    }

    public void swapItem(UUID id, World world, ItemStack newItem) {
        if (id == null || world == null) return;
        Entity e = world.getEntity(id);
        if (e instanceof ItemDisplay disp && disp.isValid()) disp.setItemStack(newItem);
    }

    public void remove(UUID id, World world) {
        if (id == null || world == null) return;
        Entity e = world.getEntity(id);
        if (e != null) e.remove();
    }

    /** Sweep every currently loaded chunk and delete any display entity left
     *  over from a previous run. Called on plugin enable. */
    public int sweepOrphans() {
        int removed = 0;
        for (World w : plugin.getServer().getWorlds()) {
            for (Chunk c : w.getLoadedChunks()) {
                for (Entity e : c.getEntities()) {
                    if (!(e instanceof Display)) continue;
                    if (Keys.OWNED_ENTITY == null) continue;
                    Byte tag = e.getPersistentDataContainer().get(Keys.OWNED_ENTITY, PersistentDataType.BYTE);
                    if (tag != null && tag != 0) {
                        e.remove();
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    private void tagOwned(Entity e) {
        if (Keys.OWNED_ENTITY == null) return;
        e.getPersistentDataContainer().set(Keys.OWNED_ENTITY, PersistentDataType.BYTE, (byte) 1);
    }
}
