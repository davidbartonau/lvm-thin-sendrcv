package oneit.lvmsendrcv.service;

import oneit.lvmsendrcv.*;
import oneit.lvmsendrcv.dd.DDRandomReceive;
import oneit.lvmsendrcv.utils.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.*;
import org.json.JSONObject;

/**
 * 
 * @author david
 */
public class LVMSnapSendService extends Thread
{
    InetAddress target;
    int         port;
    String      vg;
    String      lv;
    String      targetPath;
    
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) 
    {
        try
        {
            Options             options = new Options();
            CommandLineParser   parser = new GnuParser();

            options.addOption("vg", "volGroup", true, "The volume group e.g. volg");
            options.addOption("lv", "logicalVolume", true, "The logical volume to snapshot and send e.g. thin_volume");
            options.addOption("to", "targetPath", true, "The full target path to write to at the destination");
            options.addOption("h", "help", false, "Print usage help.");

            CommandLine  cmdLine = parser.parse(options, args);

            if (cmdLine.hasOption("h"))
            {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "oneit.lvmsendrcv.LVMSnapSend", 
                                     "Determines the difference between two thin LVM snapshots and sends the differences.", 
                                     options,
                                     null);
                return;
            }
            else
            {
                String                          vg          = Utils.getMandatoryString("vg", cmdLine, options);
                String                          lv          = Utils.getMandatoryString("lv", cmdLine, options);
                String                          targetPath  = Utils.getMandatoryString("to", cmdLine, options);
                LVMSnapSendService              sender      = new LVMSnapSendService(InetAddress.getLocalHost(), 15432, vg, lv, targetPath);
                
                sender.start();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(1);
        }
    }

    
    public LVMSnapSendService(InetAddress target, int port, String vg, String lv, String targetPath) 
    {
        this.target = target;
        this.port = port;
        this.vg = vg;
        this.lv = lv;
        this.targetPath = targetPath;
    }


    
    @Override
    public void run() 
    {
        while (true)
        {
            connectAndSend();
            
            try 
            {
                Thread.sleep(10000);
            } 
            catch (InterruptedException ex) 
            {
                return;
            }
        }
    }

    
    public void connectAndSend () 
    {
        try (Socket  connectToServer = new Socket(target, port))
        {
            System.err.println("Connecting to server:" + target + ":" + port);
            
            PrintStream     out = new PrintStream(new BufferedOutputStream(connectToServer.getOutputStream()));
            InputStream     in = connectToServer.getInputStream();
            LVMSnapSend     sender = new LVMSnapSend(vg, lv, targetPath, out);
            
            sender.createSnapshotsAndSend();
            out.flush();
            connectToServer.shutdownOutput();
            
            System.err.println("Ready for response:");

            String  response = IOUtils.readToByte(in, (byte)0);
            
            if (response.trim().equals("OK"))
            {
                System.err.println("Read response Good:" + response);
            }
            else
            {
                System.err.println("Read response BAD:" + response);
            }
            
            connectToServer.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
