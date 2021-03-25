package org.wensheng.wsmcpi;

import java.sql.*;
import java.util.*;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class WSMCPI extends JavaPlugin implements Listener{
    final Logger logger = Logger.getLogger("WSMCPI");
    private static final Set<Material> blockBreakDetectionTools = EnumSet.of(
            Material.DIAMOND_SWORD,
            Material.GOLDEN_SWORD,
            Material.IRON_SWORD, 
            Material.STONE_SWORD,
            Material.WOODEN_SWORD);

    private WSServer wsServer = null;
    private final int httpServerPort = 8686;
    private HttpServer httpServer = null;
    HashMap<String, List<String>> userFunctions = new HashMap<>();
    Connection sqlconn = null;

    public void onEnable(){
        this.saveDefaultConfig();
        save_resources();

        int wss_port = getConfig().getInt("port");
        if(wss_port==0){
            wss_port = 4721;
        }

        if(wsServer == null) {
            wsServer = new WSServer(this, wss_port);
            wsServer.start();
        }

        //register the events
        //Bukkit.getPluginManager().registerEvents(this,this);
        getServer().getPluginManager().registerEvents(this, this);
        //setup the schedule to called the tick handler
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new TickHandler(), 1, 1);
        if(getConfig().getBoolean("start_http_server")) {
            createHttpServer();
        }
        String url = String.format("jdbc:sqlite:%s/%s", getDataFolder().toString(), "db.sqlite");
        // create a connection to the database
        try {
            sqlconn = DriverManager.getConnection(url);
            String sql = "CREATE TABLE IF NOT EXISTS playerfuncs (\n"
                         + "    id integer primary key,\n"
                         + "    player text NOT NULL,\n"
                         + "    funcname text,\n"
                         + "    functext text\n"
                         + ");";
            Statement stmt = sqlconn.createStatement();
            stmt.execute(sql);
        } catch (SQLException e) {
            logger.warning(e.getMessage());
        }
    }

    private void save_resources() {
        File index_file = new File(getDataFolder(), "index.html");
        if(!index_file.exists()){
            this.saveResource("index.html", false);
        }
    }

    private void createHttpServer(){
        try {
            httpServer = HttpServer.create(new InetSocketAddress(httpServerPort), 0);
            byte[] response = Files.readAllBytes(Paths.get(getDataFolder().toString(),"index.html"));
            httpServer.createContext("/", httpExchange ->
            {
                httpExchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                httpExchange.sendResponseHeaders(200, response.length);
                OutputStream out = httpExchange.getResponseBody();
                out.write(response);
                out.close();
            });
            httpServer.start();
        } catch (IOException e){
            logger.warning("Could not start HTTP Server");
        }
    }

    public void onDisable(){
        getServer().getScheduler().cancelTasks(this);
        if(wsServer != null){
            try {
                wsServer.stop();
            } catch (IOException | InterruptedException e){
                logger.warning("Can not stop websocket server");
            }
        }
        if(getConfig().getBoolean("start_http_server") && httpServer != null) {
            httpServer.stop(0);
        }
    }
    
    private class TickHandler implements Runnable {
        public void run() {
            for(RemoteSession session: wsServer.getHandlers().values()){
                if(!session.pendingRemoval){
                    session.tick();
                }
            }
        }
    }
    
    @EventHandler(ignoreCancelled=true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack currentTool = event.getPlayer().getInventory().getItemInMainHand();
        if (!blockBreakDetectionTools.contains(currentTool.getType())) {
            return;
        }
        for(RemoteSession session: wsServer.getHandlers().values()){
            session.queuePlayerInteractEvent(event);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onChatPosted(AsyncPlayerChatEvent event) {
        for(RemoteSession session: wsServer.getHandlers().values()){
            session.queueChatPostedEvent(event);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        String newMessage = event.getMessage().toLowerCase();
        String[] msgs = event.getMessage().split(" ");
        Player p = event.getPlayer();
        if(userFunctions.containsKey(p.getName())) {
            List ufs = userFunctions.get(p.getName());
            if (ufs.contains(msgs[0])) {
                if(msgs[0].equals("myfuncs")){
                    if(msgs.length > 1 && msgs[1].equals("clear")){
                        String sql = "delete from playerfuncs where player=?";
                        try (PreparedStatement stmt = sqlconn.prepareStatement(sql)) {
                            stmt.setString(1, p.getName());
                            stmt.executeUpdate();
                            userFunctions.put(p.getName(), new ArrayList<>(Arrays.asList("myfuncs")));
                        } catch (SQLException e) {
                            logger.warning(e.getMessage());
                            return;
                        }
                    }else {
                        p.sendMessage(String.join(" ", ufs));
                    }
                    return;
                }
                String cmdStr = "";
                String sql = String.format("select functext from playerfuncs \n"
                        + "where player='%s' and funcname='%s'", p.getName(), msgs[0]);
                try (Statement stmt = sqlconn.createStatement();
                    ResultSet rs = stmt.executeQuery(sql)) {
                    if (rs.next()) {
                        cmdStr = rs.getString("functext");
                    }
                } catch (SQLException e) {
                    logger.warning(e.getMessage());
                    return;
                }
                new PlayerFuncExecutor(this, p, cmdStr).runTaskLater(this, 2);
            }
        }
        //event.setMessage(newMessage);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event){
        Player p = event.getPlayer();
        List fNames = new ArrayList<String>();
        fNames.add("myfuncs");
        userFunctions.put(p.getName(), fNames);

        String sql = String.format("select funcname from playerfuncs \n"
                + "where player='%s'", p.getName());
        try (Statement stmt = sqlconn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                fNames.add(rs.getString("funcname"));
            }
        } catch (SQLException e) {
            logger.warning(e.getMessage());
            return;
        }
        userFunctions.put(p.getName(), fNames);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        userFunctions.remove(p.getName());
    }

    Player getNamedPlayer(String name) {
        if (name == null) return null;
        for(Player p: Bukkit.getOnlinePlayers()){
            if(name.equalsIgnoreCase(p.getName())){
                return p;
            }
        }
        return null;
    }
}
