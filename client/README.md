# WSMCPI Client Examples

These are websocket clients that communicate with websocket server embedded in WSMCPI plugin.

They are intefaces that take user inputs and translate them to API commands and send them to WSMCPI plugin of Spigot.   WSMCPI excutes these commands on Spigot serverto change the game world.


## API Commands

WSMCPI has API's similiar to MinecraftPi.  You can use [this page](https://www.stuffaboutcode.com/p/minecraft-api-reference.html) as API reference.

WSMCPI implements some additional API commands. (todo: doc)

API commands use "Namespace ID"s such as "minecraft:gold_block", "minecraft:zombie".  For a completed list of ID's, see [this page](https://minecraft.fandom.com/wiki/Java_Edition_data_values).  You can omit the "minecraft:" part.  But if you want to use a custom item, you must use fullname such as "mycustommod:dracula".
