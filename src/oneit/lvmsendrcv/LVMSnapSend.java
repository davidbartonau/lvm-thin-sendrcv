package oneit.lvmsendrcv;

import java.io.*;
import java.sql.*;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.xpath.XPathExpressionException;
import org.w3c.dom.*;
import org.apache.commons.cli.*;
import org.xml.sax.SAXException;

/**
 * 
 * @author david
 */
public class LVMSnapSend 
{

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
            options.addOption("s1", "snapShot1", true, "The first snapshot e.g. snapshot1");
            options.addOption("s2", "snapShot2", true, "The second snapshot e.g. snapshot2");
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
                String                          vg      = cmdLine.getOptionValue("vg");
                String                          snap1   = cmdLine.getOptionValue("s1");
                String                          snap2   = cmdLine.getOptionValue("s2");
                LVMBlockDiff.LVMSnapshotDiff    diff    = LVMBlockDiff.diffLVMSnapshots(vg, snap1, snap2);
                int[]                           blocks  = new int[diff.getDifferentBlocks().size()];
                
                for (int x = 0 ; x < blocks.length ; ++x)
                {
                    blocks[x] = diff.getDifferentBlocks().get(x).intValue();
                }
                
                System.err.println(diff.getDifferentBlocks()); // @todo
                System.err.println("Blocks changed:" + diff.getDifferentBlocks().size()); // @todo
                setLVMActivationStatus(vg, snap2, "y");
                
                try
                {
                    DDRandomSend    ddRandomSend = new DDRandomSend(diff.getChunkSizeKB() * 1024, "/dev/" + vg + "/" + snap2, blocks);
                
                    ddRandomSend.readData();
                }
                finally
                {
                    setLVMActivationStatus(vg, snap2, "n");
                }                
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(1);
        }
    }   
    
    
    private static void setLVMActivationStatus (String vg, String lv, String status) throws InterruptedException, IOException
    {
        // lvchange -ay -Ky storage/snap1
        new ExecUtils.ExecuteProcess ("/tmp/", "/sbin/lvchange", "-a" + status, "-Ky", vg + "/" + lv).setHideStdOut(true).executeAsBytes();
    }
}
