package oneit.lvmsendrcv;

import oneit.lvmsendrcv.utils.ExecUtils;
import java.io.*;
import java.util.*;
import org.json.*;

/**
 *
 * @author david
 */
public class LVM 
{
    /**
     * getThinPoolInfo ("volg", "volg-thinpool")
     * @param vgName
     * @param thinPool
     * @return
     */
    public static LVMThinPool getThinPoolInfo (String vgName, String thinPool) throws LVMException
    {
        // EXEC lvs --reportformat json -o chunksize,lv_dm_path ${thinPool}
        List<JSONObject>            thinPools = execLVS(LVMThinPool.LVS_COLUMNS, new String[] { vgName + "/" + thinPool });

        if (thinPools.size() != 1)
        {
            throw new LVMException("Thinpools.length != 1 : " + thinPools.size() + " " + thinPools);
        }
        
        return new LVMThinPool(thinPools.get(0));
    }
    
    
    /**
     * getSnapshotInfo ("volg/thin_volume_snap2", "volg/thin_volume_snap1")
     * @param vgName
     * @param snapshotNames 
     * @return map of LVMSnapshot keyed by name
     */
    public static Map<String, LVMSnapshot> getSnapshotInfo (String[] snapshotNames) throws LVMException
    {
        // EXEC lvs --reportformat json -o lv_name,vg_name,pool_lv,thin_id volg/thin_volume_snap2 volg/thin_volume_snap1
        List<JSONObject>            snapshots = execLVS(LVMSnapshot.LVS_COLUMNS, snapshotNames);
        Map<String, LVMSnapshot>    result = new HashMap<>();

        for (JSONObject  lvsnapJSON : snapshots)
        {
            LVMSnapshot lvsnap = new LVMSnapshot(lvsnapJSON);
            
            result.put(lvsnap.snapshotName, lvsnap);
        }
        
        return result;
    }

    
    public static List<JSONObject> execLVS (String columns, String[] vols) throws LVMException
    {
        List<String>    lvsArgs         = new ArrayList<>();
        String          lvsOutputStr;
        
        lvsArgs.addAll(Arrays.asList("/sbin/lvs", "--reportformat", "json", "-o", columns));
        lvsArgs.addAll(Arrays.asList(vols));
        
        try
        {
            byte[]          lvsOutput           = new ExecUtils.ExecuteProcess ("/tmp/", lvsArgs.toArray(new String[0])).setHideStdOut(true).executeAsBytes();
            
            lvsOutputStr        = new String (lvsOutput);
        }
        catch (Exception e)
        {
            System.err.println("problem running lvs:" + e.getMessage()); //@todo
            throw new LVMException(e);
        }
        
        try
        {
            JSONObject          lvsOutputJSON   = new JSONObject(lvsOutputStr);
            JSONArray           lvsOutputArray  = lvsOutputJSON.getJSONArray("report").getJSONObject(0).getJSONArray("lv");
            List<JSONObject>    result          = new ArrayList<>();

            for (int x = 0 ; x < lvsOutputArray.length() ; ++x)
            {
                JSONObject  outputRow = lvsOutputArray.getJSONObject(x);
                
                result.add (outputRow);
            }

            return result;
        }
        catch (Exception e)
        {
            System.err.println("problem parsing lvs output:" + e.getMessage() + "\n" + lvsOutputStr); //@todo
            throw new LVMException(e);
        }
        
    }

    public static class LVMSnapshot
    {
        static final String LVS_COLUMNS = "lv_name,vg_name,pool_lv,thin_id";
        
        public final String  snapshotName;
        public final String  vgName;
        public final String  poolLV;
        public final Integer thinID;

        public LVMSnapshot(JSONObject lvsnap) throws LVMException
        {
            try
            {
                String              thinIDStr = lvsnap.getString("thin_id");
                
                this.snapshotName   = lvsnap.getString("lv_name");
                this.vgName         = lvsnap.getString("vg_name");
                this.poolLV         = lvsnap.getString("pool_lv");
                this.thinID         = thinIDStr.equals("") ? null : Integer.parseInt(thinIDStr);
            }
            catch (Exception e)
            {
                System.err.println("problem parsing lvs output:" + e.getMessage() + "\n" + lvsnap); //@todo
                throw new LVMException(e);
            }
        }

        
        @Override
        public String toString() 
        {
            return "LVSnapshot " + vgName + "/" + snapshotName + " in " + poolLV + "@" + thinID;
        }
    }
    
    
    public static class LVMThinPool
    {
        static final String LVS_COLUMNS = "lv_name,vg_name,chunksize,lv_dm_path";
        
        public final String  name;
        public final String  vgName;
        public final int     chunkSizeKB;
        public final String  dmPath;

        public LVMThinPool(JSONObject thinJSON) throws LVMException
        {
            try
            {
                String      chunkSizeStr        = thinJSON.getString("chunk_size");

                assert chunkSizeStr.charAt(chunkSizeStr.length() - 1) == 'k' : "Chunk size must be in kB";

                this.name           = thinJSON.getString("lv_name");
                this.vgName         = thinJSON.getString("vg_name");
                this.chunkSizeKB    = (int)Float.parseFloat(chunkSizeStr.substring(0, chunkSizeStr.length() - 1));
                this.dmPath         = thinJSON.getString("lv_dm_path");
            }
            catch (JSONException e)
            {
                System.err.println("problem parsing lvs output:" + e.getMessage() + "\n" + thinJSON); //@todo
                throw new LVMException(e);
            }
        }
        
        @Override
        public String toString() 
        {
            return "LVThinPool " + vgName + "/" + name + "  " + chunkSizeKB + " @ " + dmPath;
        }
    }

    
    public static class LVMException extends Exception
    {
        public LVMException(Exception cause) 
        {
            super (cause);
        }
        
        public LVMException(String message) 
        {
            super (message);
        }
    }
}
