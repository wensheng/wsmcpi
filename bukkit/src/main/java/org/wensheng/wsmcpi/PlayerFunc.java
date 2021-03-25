package org.wensheng.wsmcpi;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.lang.reflect.Method;

public class PlayerFunc {
    private WSMCPI plugin = null;
    private Player player = null;

    @Expose
    public String funcType;

    @Expose
    public String actionType;

    @Expose
    public List<String[]> commands;

    PlayerFunc() {
    }

    void run(WSMCPI plugin, Player player) {
        this.plugin = plugin;
        this.player = player;

        String[] first = commands.get(0);
        try {
            String mName = String.format("%s_%s", actionType, first[0]);
            Method m = PlayerFunc.class.getDeclaredMethod(mName, String[].class);
            m.invoke(this, (Object)Arrays.copyOfRange(first, 1, first.length));
        }catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e){
            plugin.logger.warning(e.getMessage());
        }

        /*
        try{
            if(funcType.equals("single")) {
                if (actionType == "player") {
                    if(!AllowedActions.player.contains(c.get(0))){
                        return;
                    }
                    // https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/entity/Player.html
                    Method m = Player.class.getMethod(c.get(0));
                    m.invoke(player, c.get(1));

                } else if (actionType == "block") {
                    // https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/World.html
                    if(!AllowedActions.block.contains(c.get(0))){
                        return;
                    }
                    Method m = World.class.getMethod(c.get(0));
                    World world = player.getWorld();
                    m.invoke(world, c.get(1));
                } else if (actionType == "mobs") {
                    if(!AllowedActions.mobs.contains(c.get(0))){
                        return;
                    }
                    Method m = World.class.getMethod(c.get(0));
                    World world = player.getWorld();
                    m.invoke(world, c.get(1));
                }
            }
        } catch (NoSuchMethodException e){

        } catch (NullPointerException e){

        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }*/
    }

    // https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/entity/Player.html
    void player_getLocation(String... args){
        if(args.length > 0) return;
        player.sendMessage(player.getLocation().toString());
    }

    void player_teleport(String... args){
        if(args.length != 3) return;
        Location loc = rLoc(args[0], args[1], args[2]);
        player.teleport(loc);
    }

    void block_place(String... args){
        if(args.length != 4) return;
        Location loc = rLoc(args[0], args[1], args[2]);
        Material material = Material.matchMaterial(args[3]);
        if(material == null){
            material = Material.valueOf("SANDSTONE");
        }
        BlockFace blockFace = BlockFace.values()[0];
        Block block = player.getWorld().getBlockAt(loc);
        block.setType(material);
        BlockData blockData = block.getBlockData();
        if(blockData instanceof Directional){
            ((Directional) blockData).setFacing(blockFace);
        }
        block.setBlockData(blockData);
        plugin.logger.info(loc.toString());
    }

    void block_fill(String... args){
        if(args.length != 7) return;

    }

    // https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/World.html
    void mob_spawn(String... args){
        if(args.length != 4) return;
        Location loc = rLoc(args[0], args[1], args[2]);
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(args[3].toUpperCase());
        } catch (Exception exc) {
            entityType = EntityType.valueOf("COW");
        }
        player.getWorld().spawnEntity(loc, entityType);
    }

    // relative location to player
    private Location rLoc(String xstr, String ystr, String zstr) {
        int x = (int) Double.parseDouble(xstr);
        int y = (int) Double.parseDouble(ystr);
        int z = (int) Double.parseDouble(zstr);
        Location loc = player.getLocation();
        return new Location(player.getWorld(), loc.getX() + x, loc.getY() + y, loc.getZ() + z);
    }
}