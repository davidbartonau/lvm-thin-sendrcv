package oneit.lvmsendrcv;

import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.xpath.XPathExpressionException;
import org.json.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 *
 * @author david
 */
public class LVMBlockDiff 
{
    public static LVMSnapshotDiff diffLVMSnapshots (String vgName, String snapshot1, String snapshot2) throws InterruptedException, IOException, JSONException, ParserConfigurationException, SAXException, XPathExpressionException
    {
        Map<String, LVMSnapshot>    snapshotInfo = getSnapshotInfo(vgName, snapshot1, snapshot2);
        
        System.err.println (snapshotInfo); // @todo
        
        String          thinPoolName    = snapshotInfo.get(snapshot1).poolLV;
        LVMThinPool     thinPool        = getThinPoolInfo(vgName, thinPoolName);
        String          snap1ID         = String.valueOf(snapshotInfo.get(snapshot1).thinID);
        String          snap2ID         = String.valueOf(snapshotInfo.get(snapshot2).thinID);

        System.err.println (thinPool); // @todo
        
        new ExecUtils.ExecuteProcess ("/tmp/", "/sbin/dmsetup", "message", thinPool.dmPath + "-tpool", "0", "reserve_metadata_snap").setHideStdOut(true).executeAsBytes();
        
        try
        {
            byte[]          thinDeltaOutput = new ExecUtils.ExecuteProcess ("/tmp/", "/usr/sbin/thin_delta", "-m", "--snap1", snap1ID, "--snap2", snap2ID,  thinPool.dmPath + "_tmeta").setHideStdOut(true).executeAsBytes();
            List<Integer>   result          = getBlocksDiffMatching (thinDeltaOutput, false, true, true, true);
            
            return new LVMSnapshotDiff(thinPool.chunkSizeKB, result);
        }
        finally
        {       
            new ExecUtils.ExecuteProcess ("/tmp/", "/sbin/dmsetup", "message", thinPool.dmPath + "-tpool", "0", "release_metadata_snap").setHideStdOut(true).executeAsBytes();
        }        
    }

    
    private static List<Integer> getBlocksDiffMatching (byte[] thin_delta_output, boolean includeSame, boolean includeLeftOnly, boolean includeRightOnly, boolean includeDifferent) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException
    {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder        dBuilder = dbFactory.newDocumentBuilder();        
        Document               thinDeltaXML = dBuilder.parse(new ByteArrayInputStream(thin_delta_output));
        List<Integer>          result = new ArrayList<>();
        
        for (Node blockNode : XMLUtils.getXPathNodes(thinDeltaXML, "superblock/diff/*"))
        {
            String  nodeName = XMLUtils.getXPathVal(blockNode, "name()");
            
            switch(nodeName) 
            { 
                case "same": 
                    addBlockRangeIf(includeSame, blockNode, result);
                    break; 
                case "right_only": 
                    addBlockRangeIf(includeRightOnly, blockNode, result);
                    break; 
                case "left_only": 
                    addBlockRangeIf(includeLeftOnly, blockNode, result);
                    break; 
                case "different": 
                    addBlockRangeIf(includeDifferent, blockNode, result);
                    break; 
                default: 
                    throw new IllegalArgumentException("Invalid block difference result:" + blockNode); 
            } 
        }
        
        return result;
    }
    
    
    private static void addBlockRangeIf(boolean includeSame, Node blockNode, List<Integer> blocks) throws XPathExpressionException 
    {
        if (includeSame)
        {
            int     begin = Integer.parseInt(XMLUtils.getXPathVal(blockNode, "@begin"));
            int     length = Integer.parseInt(XMLUtils.getXPathVal(blockNode, "@length"));
            
            for (int x = 0 ; x < length ; ++x)
            {
                blocks.add(begin + x);
            }
        }        
    }

    /**
     * getThinPoolInfo ("volg", "volg-thinpool")
     * @param vgName
     * @param thinPool
     * @return
     */
    private static LVMThinPool getThinPoolInfo (String vgName, String thinPool) throws InterruptedException, IOException, JSONException
    {
        // EXEC lvs --reportformat json -o chunksize,lv_dm_path ${thinPool}
        byte[]      lvsOutput           = new ExecUtils.ExecuteProcess ("/tmp/", "/sbin/lvs", "--reportformat", "json", "-o", "chunksize,lv_dm_path", vgName + "/" + thinPool).setHideStdOut(true).executeAsBytes();
        JSONObject  lvsOutputJSON       = new JSONObject(new String (lvsOutput));
        JSONObject  thinDetails         = lvsOutputJSON.getJSONArray("report").getJSONObject(0).getJSONArray("lv").getJSONObject(0);
        String      chunkSizeStr        = thinDetails.getString("chunk_size");
        
        assert chunkSizeStr.charAt(chunkSizeStr.length() - 1) == 'k' : "Chunk size must be in kB";
        
        return new LVMThinPool(thinPool, 
                               vgName,
                               Math.round(Float.parseFloat(chunkSizeStr.substring(0, chunkSizeStr.length() - 1))), 
                               thinDetails.getString("lv_dm_path"));
    }
    
    
    /**
     * getSnapshotInfo ("volg", "thin_volume_snap2", "thin_volume_snap1")
     * @param vgName
     * @param snapshot1
     * @param snapshot2
     * @return map of LVMSnapshot keyed by name
     */
    private static Map<String, LVMSnapshot> getSnapshotInfo (String vgName, String snapshot1, String snapshot2) throws InterruptedException, IOException, JSONException
    {
        // EXEC lvs --reportformat json -o lv_name,vg_name,pool_lv,thin_id volg/thin_volume_snap2 volg/thin_volume_snap1
        Map<String, LVMSnapshot>    result = new HashMap<>();
        byte[]      lvsOutput           = new ExecUtils.ExecuteProcess ("/tmp/", "/sbin/lvs", "--reportformat", "json", "-o", "lv_name,vg_name,pool_lv,thin_id", vgName + "/" + snapshot1, vgName + "/" + snapshot2).setHideStdOut(true).executeAsBytes();
        JSONObject  lvsOutputJSON       = new JSONObject(new String (lvsOutput));
        JSONArray   lvsnapArray             = lvsOutputJSON.getJSONArray("report").getJSONObject(0).getJSONArray("lv");
        
        for (int x = 0 ; x < lvsnapArray.length() ; ++x)
        {
            JSONObject  lvsnap = lvsnapArray.getJSONObject(x);
            String      snapName = lvsnap.getString("lv_name");
            
            result.put(snapName, new LVMSnapshot(snapName, 
                                                     lvsnap.getString("vg_name"), 
                                                     lvsnap.getString("pool_lv"), 
                                                     Integer.parseInt(lvsnap.getString("thin_id"))));
        }
        
        return result;
    }


    public static class LVMSnapshotDiff 
    {
        private int             chunkSizeKB;
        private List<Integer>   differentBlocks;

        
        public LVMSnapshotDiff(int chunkSizeKB, List<Integer> differentBlocks) 
        {
            this.chunkSizeKB = chunkSizeKB;
            this.differentBlocks = differentBlocks;
        }

        
        public int getChunkSizeKB() 
        {
            return chunkSizeKB;
        }

        
        public List<Integer> getDifferentBlocks() 
        {
            return differentBlocks;
        }
    }

    
    private static class LVMSnapshot
    {
        public String  snapshotName;
        public String  vgName;
        public String  poolLV;
        public int     thinID;

        public LVMSnapshot(String snapshotName, String vgName, String poolLV, int thinID) 
        {
            this.snapshotName = snapshotName;
            this.vgName = vgName;
            this.poolLV = poolLV;
            this.thinID = thinID;
        }

        @Override
        public String toString() 
        {
            return "LVSnapshot " + vgName + "/" + snapshotName + " in " + poolLV + "@" + thinID;
        }
    }
    
    
    private static class LVMThinPool
    {
        public String  name;
        public String  vgName;
        public int     chunkSizeKB;
        public String  dmPath;

        public LVMThinPool(String name, String vgName, int chunkSizeKB, String dmPath) 
        {
            this.name = name;
            this.vgName = vgName;
            this.chunkSizeKB = chunkSizeKB;
            this.dmPath = dmPath;
        }
        
        @Override
        public String toString() 
        {
            return "LVThinPool " + vgName + "/" + name + "  " + chunkSizeKB + " @ " + dmPath;
        }
    }
}
