## Developing WSMCPI

This guide set your programming environment for developing WSMCPI plugin for Spigot.  Please note as an example I use version 1.16.5, which at the time of this writing is the latest version of Spigot. You should replace `1.16.5` with whatever version you want to use.  Also here I use JDK version 11, but you can use any Java version from 8 onward.

### Spigot Installation

In a command window (cmd.exe or powershell, or a Mac terminal): 

```
mkdir mc1.16.5
cd mc1.16.5
mkdir build
mkdir run
cd build
```

Download [BuildTools.jar](https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar) into build directory, then:
```
java -jar BuildTools.jar --rev 1.16.5
```
Copy `spigot-1.16.5.jar` to run/ directory and rename it to just `spigot.jar`


In run/ folder, create file `start-server-win.bat` with the following:

```
echo off
chcp 437
java -Xms8G -Xmx8G -jar spigot.jar --nogui
pause
```

For Mac, just put the `java` line in start-server-mac.sh, then do `chmod +x start-server-mac.sh`. 

Run `start-server-win.bat` (or `start-server-mac.sh`).

Change `eula.txt` and run `start-server-[win.bat|mac.sh]` again, type `stop` to exit.

### IntelliJ IDEA Configuration

Open File -> Settings, select Plugins, install "Minecraft Development" plugin.

Clone or download this repo, then in IntelliJ Idea, open File -> Open..., open WSMCPI/bukkit. The project is now opened.

Edit pom.xml, change "Version" and "SpigotVersion" to 1.16.5 or whatever you're using.

Click View -> "Tool Windows" -> Maven, in Maven panel, double click wsmcpi -> Lifecycle -> pacakage.
