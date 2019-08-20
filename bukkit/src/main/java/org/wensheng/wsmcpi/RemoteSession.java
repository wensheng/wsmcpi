package org.wensheng.wsmcpi;

import java.io.*;
import java.net.*;
import java.util.*;

import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.*;
import org.bukkit.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
//import org.bukkit.material.Directional;
import org.bukkit.material.SimpleAttachableMaterialData;
import org.java_websocket.WebSocket;

public class RemoteSession {
    private Location origin;
    private WebSocket socket;
    private BufferedWriter out;
    private Thread outThread;
    private ArrayDeque<String> inQueue = new ArrayDeque<String>();
    public boolean running = true;
    public boolean pendingRemoval = false;
    public WSMCPI plugin;
    protected ArrayDeque<PlayerInteractEvent> interactEventQueue = new ArrayDeque<PlayerInteractEvent>();
    protected ArrayDeque<AsyncPlayerChatEvent> chatPostedQueue = new ArrayDeque<AsyncPlayerChatEvent>();
    private int maxCommandsPerTick = 9000;
    private boolean closed = false;
    private Player attachedPlayer = null;
    private final List<String> queuedCommands = Arrays.asList(new String[]{
            "world.setBlock",
            "world.setBlocks",
            "world.spawnEntity"
    });

    public RemoteSession(WSMCPI plugin, WebSocket socket) throws IOException {
        this.socket = socket;
        this.plugin = plugin;
        plugin.getLogger().info("Opened connection to" + socket.getRemoteSocketAddress() + ".");
    }

    public Location getOrigin() {
        return origin;
    }

    public void setOrigin(Location origin) {
        this.origin = origin;
    }

    public WebSocket getSocket() {
        return socket;
    }

    public void queuePlayerInteractEvent(PlayerInteractEvent event) {
        //plugin.getLogger().info(event.toString());
        interactEventQueue.add(event);
    }

    public void queueChatPostedEvent(AsyncPlayerChatEvent event) {
        //plugin.getLogger().info(event.toString());
        chatPostedQueue.add(event);
    }

    /** called from the server main thread */
    public void tick() {
        if (origin == null) this.origin = plugin.getServer().getWorlds().get(0).getSpawnLocation();
        int processedCount = 0;
        String message;
        while ((message = inQueue.poll()) != null) {
            String methodName = message.substring(0, message.indexOf("("));
            //split string into args, handles , inside " i.e. ","
            String[] args = message.substring(message.indexOf("(") + 1, message.length() - 1).split(",");
            handleCommand(methodName, args);
            processedCount++;
            if (processedCount >= maxCommandsPerTick) {
                plugin.getLogger().warning("Over " + maxCommandsPerTick +
                    " commands were queued - deferring " + inQueue.size() + " to next tick");
                break;
            }
        }

        if (!running && inQueue.size() <= 0) {
            pendingRemoval = true;
        }
    }

    protected void handleLine(String line) {
        if(line.indexOf("(") == -1 || line.indexOf(")") == -1){
           socket.send("Wrong format");
           return;
        }
        String methodName = line.substring(0, line.indexOf("("));
        //split string into args, handles , inside " i.e. ","
        String[] args = line.substring(line.indexOf("(") + 1, line.length() - 1).split(",");
        if(queuedCommands.contains(methodName)) {
            inQueue.add(line);
        }else{
            handleCommand(methodName, args);
        }
    }

    protected void handleCommand(String c, String[] args) {
        
        try {
            Server server = plugin.getServer();
            World world = origin.getWorld();
            
            if (c.equals("world.getBlock")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                //send(world.getBlockAt(loc).getType());
                send(world.getBlockAt(loc).getType().name());
            } else if (c.equals("world.getBlocks")) {
                Location loc1 = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Location loc2 = parseRelativeBlockLocation(args[3], args[4], args[5]);
                send(getBlocks(loc1, loc2));
            } else if (c.equals("world.getBlockWithData")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block block = world.getBlockAt(loc);
                //send(block.getType() + "," + block.getBlockData());
                send(block.getType().name() + "," + block.getBlockData());
            } else if (c.equals("world.setBlock")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Material material = Material.matchMaterial(args[3]);
                if(material == null){
                    material = Material.valueOf("SANDSTONE");
                }
                int facing = args.length > 4? Integer.parseInt(args[4]): 0;
                BlockFace blockFace = BlockFace.values()[facing];
                updateBlock(world, loc, material, blockFace);
            } else if (c.equals("world.setBlocks")) {
                Location loc1 = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Location loc2 = parseRelativeBlockLocation(args[3], args[4], args[5]);
                Material material = Material.matchMaterial(args[6]);
                if(material == null){
                    material = Material.valueOf("SANDSTONE");
                }
                int facing = args.length > 7? Integer.parseInt(args[7]): 0;
                BlockFace blockFace = BlockFace.values()[facing];
                setCuboid(loc1, loc2, material, blockFace);
            } else if (c.equals("world.getPlayerIds")) {
                StringBuilder bdr = new StringBuilder();
                for (Player p: server.getOnlinePlayers()) {
                    bdr.append(p.getEntityId());
                    bdr.append("|");
                }
                bdr.deleteCharAt(bdr.length()-1);
                send(bdr.toString());
            } else if (c.equals("world.getPlayerId")) {
                Player p = plugin.getNamedPlayer(args[0]);
                if (p != null) {
                    send(p.getEntityId());
                } else {
                    plugin.getLogger().info("Player [" + args[0] + "] not found.");
                    send("Fail");
                }
            } else if (c.equals("world.setSign")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);
                // in 1.14
                //ACACIA BIRCH OAK DARK_OAK JUNGLE SPRUCE LEGACY +_SIGN +_WALL_SIGN
                // in 1.13
                // SIGN WALL_SIGN, LEGACY 
                Material material = Material.matchMaterial(args[3]);
                if(material == null){
                    material = Material.LEGACY_SIGN;
                }
                thisBlock.setType(material);

                int facing = args.length > 4? Integer.parseInt(args[4]): 0;
                BlockFace blockFace = BlockFace.values()[facing];
                BlockData blockData = thisBlock.getBlockData();
                if(blockData instanceof org.bukkit.block.data.type.WallSign){
                    org.bukkit.block.data.type.WallSign s = (org.bukkit.block.data.type.WallSign) thisBlock.getBlockData();
                    s.setFacing(blockFace);
                    thisBlock.setBlockData(s);
                }else{
                    org.bukkit.block.data.type.Sign s = (org.bukkit.block.data.type.Sign) thisBlock.getBlockData();
                    s.setRotation(blockFace);
                    thisBlock.setBlockData(s);
                }

                BlockState signState = thisBlock.getState();
                if (signState instanceof Sign) {
                    Sign sign = (Sign) signState;
                    for (int i = 5; i - 5 < 4 && i < args.length; i++) {
                        sign.setLine(i - 5, args[i]);
                    }
                    sign.update();
                }
            } else if (c.equals("world.spawnEntity")) {
                 Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                 EntityType entityType;
                 try{
                     entityType = EntityType.valueOf(args[3].toUpperCase());
                 }catch(Exception exc){
                     entityType = EntityType.valueOf("COW");
                 }finally{
                 }
                 Entity entity = world.spawnEntity(loc, entityType);
                 send(entity.getEntityId());
            } else if (c.equals("world.getHeight")) {
                send(world.getHighestBlockYAt(parseRelativeBlockLocation(args[0], "0", args[1])) - origin.getBlockY());
            } else if (c.equals("chat.post")) {
                //create chat message from args as it was split by ,
                String chatMessage = "";
                int count;
                for(count=0;count<args.length;count++){
                    chatMessage = chatMessage + args[count] + ",";
                }
                chatMessage = chatMessage.substring(0, chatMessage.length() - 1);
                server.broadcastMessage(chatMessage);
            } else if (c.equals("events.clear")) {
                interactEventQueue.clear();
                chatPostedQueue.clear();
            } else if (c.equals("events.block.hits")) {
                StringBuilder b = new StringBuilder();
                 PlayerInteractEvent event;
                while ((event = interactEventQueue.poll()) != null) {
                    Block block = event.getClickedBlock();
                    Location loc = block.getLocation();
                    b.append(blockLocationToRelative(loc));
                    b.append(",");
                    b.append(blockFaceToNotch(event.getBlockFace()));
                    b.append(",");
                    b.append(event.getPlayer().getEntityId());
                    if (interactEventQueue.size() > 0) {
                        b.append("|");
                    }
                }
                //DEBUG
                //System.out.println(b.toString());
                send(b.toString());
            } else if (c.equals("events.chat.posts")) {
                StringBuilder b = new StringBuilder();
                AsyncPlayerChatEvent event;
                while ((event = chatPostedQueue.poll()) != null) {
                    b.append(event.getPlayer().getEntityId());
                    b.append(",");
                    b.append(event.getMessage());
                    if (chatPostedQueue.size() > 0) {
                        b.append("|");
                    }
                }
                //DEBUG
                //System.out.println(b.toString());
                send(b.toString());
            } else if (c.startsWith("player.")){
                handlePlayerCommand(c.substring(7, c.length()), args);
            } else if (c.startsWith("entity.")){
                handleEntityCommand(c.substring(7, c.length()), args);
            } else {
                plugin.getLogger().warning(c + " is not supported.");
                send("Fail");
            }
        } catch (Exception e) {
            
            plugin.getLogger().warning("Error occured handling command");
            e.printStackTrace();
            send("Fail");
            
        }
    }

    void handlePlayerCommand(String c, String[] args) {
        String name = null;
        if((c.startsWith("set") && args.length > 3) || args.length == 1) {
            name = args[0];
        }
        Player currentPlayer = getCurrentPlayer(name);
        if(currentPlayer == null){
            send("No player");
            return;
        }

        if (c.equals("getTile")) {
            send(blockLocationToRelative(currentPlayer.getLocation()));
        } else if (c.equals("setTile")) {
            String x = args[0], y = args[1], z = args[2];
            if (args.length > 3) {
                x = args[1]; y = args[2]; z = args[3];
            }
            Location loc = currentPlayer.getLocation();
            currentPlayer.teleport(parseRelativeBlockLocation(x, y, z, loc.getPitch(), loc.getYaw()));
        } else if (c.equals("getPos")) {
            send(locationToRelative(currentPlayer.getLocation()));
        } else if (c.equals("setPos")) {
            String x = args[0], y = args[1], z = args[2];
            if (args.length > 3) {
                x = args[1]; y = args[2]; z = args[3];
            }
            Location loc = currentPlayer.getLocation();
            currentPlayer.teleport(parseRelativeLocation(x, y, z, loc.getPitch(), loc.getYaw()));
        } else if (c.equals("getDirection")) {
            send(currentPlayer.getLocation().getDirection().toString());
        } else if (c.equals("getRotation")) {
            send(currentPlayer.getLocation().getYaw());
        } else if (c.equals("getPitch")) {
            send(currentPlayer.getLocation().getPitch());
        }else{
            send("No such player command");
        }
    }

    void handleEntityCommand(String c, String[] args) {
        if (args.length < 1) {
            send("Missing entity ID");
            return;
        }
        Player entity = plugin.getEntity(Integer.parseInt(args[0]));
        if (entity == null) {
            send("Failed getting entity " + args[0]);
            return;
        }
        Location loc = entity.getLocation();
        if (c.equals("getTile")) {
            send(blockLocationToRelative(loc));
        } else if (c.equals("entity.setTile")) {
            String x = args[1], y = args[2], z = args[3];
            entity.teleport(parseRelativeBlockLocation(x, y, z, loc.getPitch(), loc.getYaw()));
        } else if (c.equals("getPos")) {
            send(locationToRelative(loc));
        } else if (c.equals("setPos")) {
            String x = args[1], y = args[2], z = args[3];
            entity.teleport(parseRelativeLocation(x, y, z, loc.getPitch(), loc.getYaw()));
        } else if (c.equals("getDirection")) {
            send(loc.getDirection().toString());
        } else if (c.equals("getRotation")) {
            send(loc.getYaw());
        } else if (c.equals("getPitch")) {
            send(loc.getPitch());
        } else {
            send("No such entity command");
        }
    }

    // create a cuboid of lots of blocks
    private void setCuboid(Location pos1, Location pos2, Material blockType, BlockFace blockFace) {
        int minX, maxX, minY, maxY, minZ, maxZ;
        World world = pos1.getWorld();
        minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        for (int x = minX; x <= maxX; ++x) {
            for (int z = minZ; z <= maxZ; ++z) {
                for (int y = minY; y <= maxY; ++y) {
                    updateBlock(world, x, y, z, blockType, blockFace);
                }
            }
        }
    }

    // get a cuboid of lots of blocks
    private String getBlocks(Location pos1, Location pos2) {
        StringBuilder blockData = new StringBuilder();

        int minX, maxX, minY, maxY, minZ, maxZ;
        World world = pos1.getWorld();
        minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        for (int y = minY; y <= maxY; ++y) {
             for (int x = minX; x <= maxX; ++x) {
                 for (int z = minZ; z <= maxZ; ++z) {
                    //blockData.append(new Integer(world.getBlockTypeIdAt(x, y, z)).toString() + ",");
                     blockData.append(world.getBlockAt(x, y, z).getType() + ",");
                }
            }
        }

        return blockData.substring(0, blockData.length() > 0 ? blockData.length() - 1 : 0);    // We don't want last comma
    }

    // updates a block
    private void updateBlock(World world, Location loc, Material blockType, BlockFace blockFace) {
        Block block = world.getBlockAt(loc);
        block.setType(blockType);
        BlockData blockData = block.getBlockData();
        if(blockData instanceof Directional){
            ((Directional) blockData).setFacing(blockFace);
        }
        block.setBlockData(blockData);
    }
    
    private void updateBlock(World world, int x, int y, int z, Material blockType, BlockFace blockFace) {
        Location loc = new Location(world, x, y, z);
        updateBlock(world, loc, blockType, blockFace);
    }
    
    // gets the current player
    public Player getCurrentPlayer(String name) {
        Player player = null;
        if(name != null){
            player = plugin.getNamedPlayer(name);
            if(player != null){
                return player;
            }
        }

        if(Bukkit.getOnlinePlayers().size() == 1){
            attachedPlayer = Bukkit.getOnlinePlayers().iterator().next();
            return attachedPlayer;
        }

        if(attachedPlayer != null){
            return attachedPlayer;
        }

        attachedPlayer = plugin.getHostPlayer();
        return attachedPlayer;
    }

    public Location parseRelativeBlockLocation(String xstr, String ystr, String zstr) {
        int x = (int) Double.parseDouble(xstr);
        int y = (int) Double.parseDouble(ystr);
        int z = (int) Double.parseDouble(zstr);
        return new Location(origin.getWorld(), origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z);
    }

    public Location parseRelativeLocation(String xstr, String ystr, String zstr) {
        double x = Double.parseDouble(xstr);
        double y = Double.parseDouble(ystr);
        double z = Double.parseDouble(zstr);
        return new Location(origin.getWorld(), origin.getX() + x, origin.getY() + y, origin.getZ() + z);
    }

    public Location parseRelativeBlockLocation(String xstr, String ystr, String zstr, float pitch, float yaw) {
        Location loc = parseRelativeBlockLocation(xstr, ystr, zstr);
        loc.setPitch(pitch);
        loc.setYaw(yaw);
        return loc;
    }

    public Location parseRelativeLocation(String xstr, String ystr, String zstr, float pitch, float yaw) {
        Location loc = parseRelativeLocation(xstr, ystr, zstr);
        loc.setPitch(pitch);
        loc.setYaw(yaw);
        return loc;
    }
    
    public String blockLocationToRelative(Location loc) {
        return (loc.getBlockX() - origin.getBlockX()) + "," + (loc.getBlockY() - origin.getBlockY()) + "," +
            (loc.getBlockZ() - origin.getBlockZ());
    }

    public String locationToRelative(Location loc) {
        return (loc.getX() - origin.getX()) + "," + (loc.getY() - origin.getY()) + "," +
            (loc.getZ() - origin.getZ());
    }

    public void send(Object a) {
        send(a.toString());
    }

    public void send(String a) {
        if (pendingRemoval) return;
        socket.send(a);
    }

    public void close() {
        if (closed) return;
        running = false;
        pendingRemoval = true;

        //wait for threads to stop
        try {
            outThread.join(2000);
        }
        catch (InterruptedException e) {
            plugin.getLogger().warning("Failed to stop in/out thread");
            e.printStackTrace();
        }

        try {
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        plugin.getLogger().info("Closed connection to" + socket.getRemoteSocketAddress() + ".");
    }

    public void kick(String reason) {
        try {
            out.write(reason);
            out.flush();
        } catch (Exception e) {
        }
        close();
    }

    /** from CraftBukkit's org.bukkit.craftbukkit.block.CraftBlock.blockFactToNotch */
    public static int blockFaceToNotch(BlockFace face) {
        switch (face) {
        case DOWN:
            return 0;
        case UP:
            return 1;
        case NORTH:
            return 2;
        case SOUTH:
            return 3;
        case WEST:
            return 4;
        case EAST:
            return 5;
        default:
            return 7; // Good as anything here, but technically invalid
        }
    }

}
