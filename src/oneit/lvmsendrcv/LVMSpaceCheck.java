package oneit.lvmsendrcv;

import java.util.*;
import oneit.lvmsendrcv.utils.*;
import org.apache.commons.cli.*;
import org.json.*;


/**
 *
 * @author david
 */
public class LVMSpaceCheck 
{
    public static Map<String, Integer>  ERROR_COUNTS = new HashMap<>();
    
    
    public static void main(String[] args) 
    {
        try
        {
            Options             options = new Options();
            CommandLineParser   parser = new GnuParser();

            options.addOption("checkInterval", true, "Loop forever and run ever [n] seconds.");
            options.addOption("h", "help", false, "Print usage help.");

            CommandLine  cmdLine = parser.parse(options, args);

            if (cmdLine.hasOption("h"))
            {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "oneit.lvmsendrcv.LVMSpaceCheck", 
                                     "Monitor the LVMs in lvmsendrcv.conf for free space.", 
                                     options,
                                     null);
                return;
            }
            else
            {
                Properties                      montorProperties = Utils.getPropertiesOf(Utils.CONFIG_PROPERTIES, "monitor.");
                Set<String>                     thinLVsToMonitor = Utils.getPropertyGroups(montorProperties, '.', false);
                Map<String, SpaceCheckLimit>    limitsByLV = new TreeMap<> ();
                int                             checkInterval= 0;
                    
                for (String thinLV : thinLVsToMonitor)
                {
                    Properties  lvProperties = Utils.getPropertiesOf(montorProperties, thinLV + ".");
                    
                    limitsByLV.put(thinLV, new SpaceCheckLimit(lvProperties));
                }
                
                if (cmdLine.hasOption("checkInterval"))
                {
                    checkInterval = Integer.parseInt(cmdLine.getOptionValue("checkInterval"));
                }
                
                if (checkInterval > 0)
                {
                    while (true)
                    {
                        checkSpace(limitsByLV);
                        Thread.sleep(checkInterval * 1000);
                    }
                }
                else
                {
                    checkSpace(limitsByLV);
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(1);
        }
    }   

    
    public static void checkSpace (Map<String, SpaceCheckLimit> limitsByLV) throws LVM.LVMException
    {
        List<JSONObject>            snapshots = LVM.execLVS("lv_name,vg_name,data_percent,metadata_percent,lv_attr", limitsByLV.keySet().toArray(new String[0]));

        for (JSONObject  snapJSON : snapshots)
        {
            try
            {
                String          lv          = snapJSON.getString("lv_name");
                String          vg          = snapJSON.getString("vg_name");
                double          metaUsage   = snapJSON.getDouble("metadata_percent");
                double          dataUsage    = snapJSON.getDouble("data_percent");
                SpaceCheckLimit limit       = limitsByLV.get(vg+ "/" + lv);

                System.err.println("Checking " + vg + "/" + lv + " meta:" + metaUsage + "/" + limit.metaLimitPercent + " data:" + dataUsage + "/" + limit.dataLimitPercent); // @todo
                checkSpace (vg, lv, limit.dataLimitPercent, dataUsage, ThinSpaceType.DATA);
                checkSpace (vg, lv, limit.metaLimitPercent, metaUsage, ThinSpaceType.METADATA);
            }
            catch (RuntimeException e)
            {
                System.err.println("Error processing lvs output:" + snapJSON + "  " + limitsByLV);
                throw e;
            }
            catch (JSONException e)
            {
                System.err.println("Error processing lvs output:" + snapJSON + " " + e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }

    
    private static void checkSpace(String vg, String lv, double limitPercent, double actualUsage, ThinSpaceType type) 
    {
        String  errorCountKey = vg + "/" + lv + ":" + type;
        
        if (limitPercent < actualUsage)
        {
            int     errorCount = 0;
            
            if (ERROR_COUNTS.containsKey(errorCountKey))
            {
                errorCount = ERROR_COUNTS.get(errorCountKey);
            }

            
            if (errorCount == 0 || errorCount == 10 || errorCount == 100 || errorCount == 1000)
            {
                String      extendCmd = (type == ThinSpaceType.DATA) ? "lvextend -L+1G " + vg + "/" + lv
                                                                     : "lvextend --poolmetadatasize +10M " + vg + "/" + lv;
                System.err.println(type + " limit of " + limitPercent + " exceeded by actual " + actualUsage + " Sending email"); // @todo
                Utils.sendEmail("[lvmsendrcv] " + type + " Limit exceeded " + lv, 
                                 "Volume:" + vg + "/" + lv + " : " + type + "\n" + 
                                 "Usage:" + actualUsage + " > " + limitPercent + "\n" + 
                                 "Count:" + errorCount + "\n" +
                                 extendCmd);
            }
            else
            {
                System.err.println(type + " limit of " + limitPercent + " exceeded by actual " + actualUsage + " Skipping email:" + errorCount); // @todo
            }
            
            ERROR_COUNTS.put(errorCountKey, errorCount + 1);
        }
        else if (ERROR_COUNTS.containsKey(errorCountKey))
        {
            System.err.println("Clearing error:" + errorCountKey); // @todo
            Utils.sendEmail("[lvmsendrcv] " + type + " Limit OK " + lv, 
                                 "Volume:" + vg + "/" + lv + " : " + type + "\n" + 
                                 "Usage:" + actualUsage + " > " + limitPercent);
            ERROR_COUNTS.remove(errorCountKey);
        }
    }
    
    
    public static class SpaceCheckLimit
    {
        public final    double  dataLimitPercent;
        public final    double  metaLimitPercent;

        public SpaceCheckLimit (Properties properties) 
        {
            this.dataLimitPercent = Double.parseDouble(properties.getProperty("dataLimit"));
            this.metaLimitPercent = Double.parseDouble(properties.getProperty("metaLimit"));
        }

        @Override
        public String toString() 
        {            
            return dataLimitPercent + " " + metaLimitPercent;
        }
        
        
    }
    
    public enum ThinSpaceType 
    {
        DATA, METADATA;
    }    
}
