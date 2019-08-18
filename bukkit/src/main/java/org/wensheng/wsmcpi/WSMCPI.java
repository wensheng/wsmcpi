package org.wensheng.wsmcpi;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.java_websocket.WebSocket;

public class WSMCPI extends JavaPlugin implements Listener{
    public final Logger logger = Logger.getLogger("Minecraft");
    public static final Set<Material> blockBreakDetectionTools = EnumSet.of(
            Material.DIAMOND_SWORD,
            Material.GOLDEN_SWORD,
            Material.IRON_SWORD, 
            Material.STONE_SWORD, 
            Material.WOODEN_SWORD);

    public List<RemoteSession> sessions;

    public Player hostPlayer = null;
    private WSServer wsServer = null;
    
    public void onEnable(){
        this.saveDefaultConfig();
        int port = this.getConfig().getInt("port");
        //boolean start_pyserver = this.getConfig().getBoolean("start_pyserver");
        
        //setup session array
        sessions = new ArrayList<RemoteSession>();
        
        try {
            wsServer = new WSServer(this, 4721);
            wsServer.start();
        } catch (IOException e) {
            getLogger().warning("Failed to start websocket server");
        }
        //register the events
        getServer().getPluginManager().registerEvents(this, this);
        //setup the schedule to called the tick handler
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new TickHandler(), 1, 1);
    }
    
    public void onDisable(){
        int port = this.getConfig().getInt("pysvr_port");
        if(wsServer != null){
            try {
                wsServer.stop();
            } catch (IOException | InterruptedException e){
                logger.warning("Can not stop websocket server");
            }
        }

        getServer().getScheduler().cancelTasks(this);
        for (RemoteSession session: sessions) {
            try {
                session.close();
            } catch (Exception e) {
                getLogger().warning("Failed to close RemoteSession");
                e.printStackTrace();
            }
        }
        
        sessions = null;
    }
    
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        String cmdString;
        int port = this.getConfig().getInt("pysvr_port");
        
        if(args.length<1){
            return false;
        }
        
        if(port==0){
            port = 32123;
        }
        
        try {
            Socket socket = new Socket("localhost", port);
            DataOutputStream toPyServer = new DataOutputStream(socket.getOutputStream());
            BufferedReader fromPyServer = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            String cmdLine = String.join(" ", args);
            toPyServer.writeUTF(cmdLine);
            //if(player instanceof Player){
            //    logger.info(player.getName() + ": send to py server: " + args[0]);
            //}
            cmdString = fromPyServer.readLine();
            logger.info("the py server send back " + cmdString);
            toPyServer.close();
            fromPyServer.close();
            socket.close();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return true;
    }
    
    private class TickHandler implements Runnable {
        public void run() {
            for(RemoteSession session: wsServer.getHandlers().values()){
                if(session.pendingRemoval==true){
                    session.close();
                    //wsServer.getHandlers().remove(entry);
                }else{
                    session.tick();
                }
            }
        }
    }
    
    @EventHandler(ignoreCancelled=true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack currentTool = event.getPlayer().getInventory().getItemInMainHand();
        if (currentTool == null || !blockBreakDetectionTools.contains(currentTool.getType())) {
            return;
        }
        for (RemoteSession session: sessions) {
            session.queuePlayerInteractEvent(event);
        }
    }
    
    @EventHandler(ignoreCancelled=true)
    public void onChatPosted(AsyncPlayerChatEvent event) {
        //debug
        //getLogger().info("Chat event fired");
        for (RemoteSession session: sessions) {
            session.queueChatPostedEvent(event);
        }
    }

    /** called when a new session is established. */
    public void handleConnection(RemoteSession newSession) {
        if (checkBanned(newSession)) {
            getLogger().warning("Kicking " + newSession.getSocket().getRemoteSocketAddress() + " because the IP address has been banned.");
            newSession.kick("You've been banned from this server!");
            return;
        }
        synchronized(sessions) {
            sessions.add(newSession);
        }
    }

    public Player getNamedPlayer(String name) {
        if (name == null) return null;
        for(Player p: Bukkit.getOnlinePlayers()){
            if(name.equalsIgnoreCase(p.getName())){
                return p;
            }
        }
        return null;
    }

    public Player getHostPlayer() {
        if (hostPlayer != null) return hostPlayer;
        Collection<? extends Player> allPlayers = Bukkit.getOnlinePlayers();
        if(allPlayers.size()>=1){
            return allPlayers.iterator().next();
        }
        return null;
    }
    
    //get entity by id - TODO to be compatible with the pi it should be changed to return an entity not a player...
    public Player getEntity(int id) {
        for (Player p: getServer().getOnlinePlayers()) {
            if (p.getEntityId() == id) {
                return p;
            }
        }
        return null;
    }

    public boolean checkBanned(RemoteSession session) {
        Set<String> ipBans = getServer().getIPBans();
        String sessionIp = session.getSocket().getRemoteSocketAddress().getAddress().toString();
        return ipBans.contains(sessionIp);
    }

}
