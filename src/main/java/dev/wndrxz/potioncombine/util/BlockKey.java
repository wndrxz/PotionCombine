package dev.wndrxz.potioncombine.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Objects;
import java.util.UUID;

public final class BlockKey {

    private final UUID worldId;
    private final int x, y, z;

    public BlockKey(UUID worldId, int x, int y, int z) {
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockKey of(Location loc) {
        return new BlockKey(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public UUID worldId() { return worldId; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }

    public Location toCenter(World world) {
        return new Location(world, x + 0.5, y + 0.5, z + 0.5);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockKey b)) return false;
        return x == b.x && y == b.y && z == b.z && worldId.equals(b.worldId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldId, x, y, z);
    }

    @Override
    public String toString() {
        return worldId + "@" + x + "," + y + "," + z;
    }
}
