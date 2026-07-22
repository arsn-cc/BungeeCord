package net.md_5.bungee.command;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Command;

public class CommandBungee extends Command
{

    public CommandBungee()
    {
        super( "bungee" );
    }

    @Override
    public void execute(CommandSender sender, String[] args)
    {
        if ( args.length > 0 && args[0].equalsIgnoreCase( "update" ) )
        {
            if ( !sender.hasPermission( "bungeecord.command.update" ) )
            {
                sender.sendMessage( ChatColor.RED + "You do not have permission to update this server." );
                return;
            }
            handleUpdate( sender );
            return;
        }

        sender.sendMessage( ChatColor.BLUE + "This server is running " + ProxyServer.getInstance().getName() + " version " + ProxyServer.getInstance().getVersion() + " by md_5." );
        sender.sendMessage( ChatColor.BLUE + "Protocol support for 1.7.x by Zartec, ghac and I9hdkill" );
        sender.sendMessage( ChatColor.BLUE + "Use /bungee update to check for and install updates." );
    }

    private void handleUpdate(CommandSender sender)
    {
        sender.sendMessage( ChatColor.YELLOW + "Checking for updates..." );

        try
        {
            URL api = new URL( "https://api.github.com/repos/arsn-cc/BungeeCord/releases/latest" );
            URLConnection con = api.openConnection();
            con.setConnectTimeout( 15000 );
            con.setReadTimeout( 15000 );

            JsonObject json = new JsonParser().parse( new InputStreamReader( con.getInputStream() ) ).getAsJsonObject();
            String tagName = json.get( "tag_name" ).getAsString();
            int latestVersion = Integer.parseInt( tagName.substring( 1 ) );

            String currentVersion = BungeeCord.getInstance().getSpecificationVersion();
            int currentBuild = "unknown".equals( currentVersion ) ? 0 : Integer.parseInt( currentVersion );

            if ( latestVersion <= currentBuild )
            {
                sender.sendMessage( ChatColor.GREEN + "You are already running the latest version (v" + currentBuild + ")." );
                return;
            }

            sender.sendMessage( ChatColor.YELLOW + "A new version is available: v" + latestVersion + " (current: v" + currentBuild + ")" );
            sender.sendMessage( ChatColor.YELLOW + "Downloading update..." );

            // Find the BungeeCord.jar file
            File currentJar = new File( BungeeCord.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath() );
            File tempJar = new File( currentJar.getParentFile(), "BungeeCord-new.jar" );

            // Download the new JAR
            URL downloadUrl = new URL( "https://github.com/arsn-cc/BungeeCord/releases/download/" + tagName + "/BungeeCord.jar" );
            URLConnection downloadCon = downloadUrl.openConnection();
            downloadCon.setConnectTimeout( 30000 );
            downloadCon.setReadTimeout( 30000 );

            try ( ReadableByteChannel rbc = Channels.newChannel( downloadCon.getInputStream() );
                  FileOutputStream fos = new FileOutputStream( tempJar ) )
            {
                fos.getChannel().transferFrom( rbc, 0, Long.MAX_VALUE );
            }

            sender.sendMessage( ChatColor.GREEN + "Update downloaded successfully." );
            sender.sendMessage( ChatColor.YELLOW + "Replacing old JAR and restarting..." );

            // Schedule restart in a separate thread to avoid blocking
            Thread updateThread = new Thread( () ->
            {
                try
                {
                    // Replace the old JAR with the new JAR
                    File backupJar = new File( currentJar.getParentFile(), "BungeeCord-old.jar" );
                    if ( backupJar.exists() )
                    {
                        backupJar.delete();
                    }
                    if ( currentJar.renameTo( backupJar ) )
                    {
                        tempJar.renameTo( currentJar );
                    }

                    // Stop the server
                    BungeeCord.getInstance().stop();
                } catch ( Exception e )
                {
                    sender.sendMessage( ChatColor.RED + "Failed to apply update: " + e.getMessage() );
                }
            }, "BungeeCord-Update" );
            updateThread.start();

        } catch ( Exception e )
        {
            sender.sendMessage( ChatColor.RED + "Failed to check for updates: " + e.getMessage() );
        }
    }
}
