package oneit.lvmsendrcv;

import oneit.lvmsendrcv.utils.ExecUtils;
import oneit.lvmsendrcv.utils.XMLUtils;
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
    public static LVMSnapshotDiff diffLVMSnapshots (String vgName, String snapshot1, String snapshot2) throws LVM.LVMException, InterruptedException, IOException
    {
        Map<String, LVM.LVMSnapshot>    snapshotInfo = LVM.getSnapshotInfo(new String[] { vgName + '/' + snapshot1, vgName + '/' + snapshot2 });
        
        System.err.println (snapshotInfo); // @todo
        
        String          thinPoolName    = snapshotInfo.get(snapshot1).poolLV;
        LVM.LVMThinPool thinPool        = LVM.getThinPoolInfo(vgName, thinPoolName);
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

    
    private static List<Integer> getBlocksDiffMatching (byte[] thin_delta_output, boolean includeSame, boolean includeLeftOnly, boolean includeRightOnly, boolean includeDifferent) throws LVM.LVMException
    {
        try
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
        catch (ParserConfigurationException | XPathExpressionException e)
        {
            System.err.println("Java configuration issue:" + e);
            throw new RuntimeException(e);
        }
        catch (IOException | SAXException e)
        {
            System.err.println("Problem parsing thin_delta output:" + e + "\n" + new String(thin_delta_output));
            throw new LVM.LVMException(e);
        }
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
}
