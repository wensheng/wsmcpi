package org.wensheng.wsmcpi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Logger;

public class PlayerFuncExecutor extends BukkitRunnable {
    private final WSMCPI plugin;
    private final Player player;
    private String cmdstr;
    private Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    PlayerFuncExecutor(WSMCPI plugin, Player player, String cmdstr) {
        this.plugin = plugin;
        this.player = player;
        this.cmdstr = cmdstr;
    }

    @Override
    public void run(){
        PlayerFunc pf = gson.fromJson(this.cmdstr, PlayerFunc.class);
        pf.run(plugin, player);
    }
}
