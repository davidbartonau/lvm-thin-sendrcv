package oneit.lvmsendrcv;

import oneit.lvmsendrcv.dd.DDRandomSend;
import oneit.lvmsendrcv.utils.Utils;
import oneit.lvmsendrcv.utils.ExecUtils;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import org.apache.commons.cli.*;
import org.json.JSONObject;

/**
 * 
 * @author david
 */
public class LVMSnapSend 
{
    public static final DateFormat    DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss");
    
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
                String                          vg      = Utils.getMandatoryString("vg", cmdLine, options);
                String                          lv      = Utils.getMandatoryString("lv", cmdLine, options);
                Map<String, LVM.LVMSnapshot>    snapshots = LVM.getSnapshotInfo(new String[] { vg });
                SortedSet<String>               sendrcvSnapshotNames = new TreeSet<>();
                
                for (String snapshotName : snapshots.keySet())
                {
                    if (snapshotName.matches(lv + "_thinsendrcv_.*"))
                    {
                        sendrcvSnapshotNames.add(snapshotName);
                    }
                }
                
                if (sendrcvSnapshotNames.size() == 1)
                {
                    String  snapshotFrom    = sendrcvSnapshotNames.first();
                    String  snapshotTo      = lv + "_thinsendrcv_" + DATE_FORMAT.format(new Date ());
                    boolean successful      = false;

                    createLVMSnapshot(vg, lv, snapshotTo);

                    try
                    {
                        snapSend(vg, snapshotFrom, snapshotTo);                
                        removeLVMSnapshot(vg, snapshotFrom);
                        successful = true;
                    }
                    finally
                    {
                        if (!successful)
                        {
                            removeLVMSnapshot(vg, snapshotTo);
                        }
                    }
                }
                else if (sendrcvSnapshotNames.size() == 0)
                {
                    String  snapshotTo      = lv + "_thinsendrcv_" + DATE_FORMAT.format(new Date ());

                    System.err.println("LVMSnapSend not intialised");
                    System.err.println("    # First take a baseline snapshot:");
                    System.err.println("    lvcreate -s -n " + snapshotTo + " " + vg + '/' + lv);
                    System.err.println("    lvchange -ay -Ky " + vg + '/' + lv);
                    System.err.println();
                    System.err.println("    # Then sent it to the destination:");
                    System.err.println("    python blocksync.py -c aes128-ctr /dev/" + vg + "/" + snapshotTo + " root@server /dev/remotevg/remotelv ");

                    System.exit(1);
                }
                else
                {
                    System.err.println("LVMSnapSend Multiple matching snapshots:" + sendrcvSnapshotNames);
                    System.exit(1);
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(1);
        }
    }   

    public static void snapSend(String vg, String snap1, String snap2) throws InterruptedException, IOException, LVM.LVMException
    {
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
    
    
    private static void setLVMActivationStatus (String vg, String lv, String status) throws InterruptedException, IOException
    {
        // lvchange -ay -Ky storage/snap1
        new ExecUtils.ExecuteProcess ("/tmp/", "/sbin/lvchange", "-a" + status, "-Ky", vg + "/" + lv).setHideStdOut(true).executeAsBytes();
    }
    
    
    private static void createLVMSnapshot (String vg, String lv, String snapshot) throws InterruptedException, IOException
    {
        // lvcreate -s -n ${LV}_snap3 ${VG}/${LV}
        new ExecUtils.ExecuteProcess ("/tmp/", "/sbin/lvcreate", "-s", "-n", snapshot, vg + "/" + lv).setHideStdOut(true).executeAsBytes();
    }
    
    
    private static void removeLVMSnapshot (String vg, String snapshot) throws InterruptedException, IOException
    {
        // lvremove -y ${VG}/${SNAPSHOT}
        new ExecUtils.ExecuteProcess ("/tmp/", "/sbin/lvremove", "-y", vg + "/" + snapshot).setHideStdOut(true).executeAsBytes();
    }
}
