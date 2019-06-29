package oneit.lvmsendrcv;

import oneit.lvmsendrcv.dd.DDRandomReceive;
import oneit.lvmsendrcv.utils.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import org.apache.commons.cli.*;
import org.json.JSONObject;

/**
 * 
 * @author david
 */
public class LVMSnapReceive
{
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) 
    {
        try
        {
            receiveSnapshot(System.in, System.out);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(1);
        }
    }

    
    public static String readToNull (InputStream in) throws IOException
    {
        StringBuilder   result = new StringBuilder();
        
        for (int charRead = in.read() ; charRead > 0 ; charRead = in.read())
        {
            result.append((char)charRead);
        }
        
        return result.toString();
    }
    
    
    public static void receiveSnapshot(InputStream in, OutputStream out) 
    {
        try
        {
            String                  targetPath = readToNull(in); // "/dev/vgreplica/thinv_replica"
            int                     blockSizeBytes = Integer.parseInt(readToNull(in));
            // @todo authentication
            
            System.err.println("Connection received for:" + targetPath + ":" + blockSizeBytes);
            
            // @todo Create temp snapshot
            
            DDRandomReceive receiver = new DDRandomReceive(blockSizeBytes, targetPath);
            
            receiver.writeData(in);
            out.write("OK".getBytes());
            
            // @todo Create final snapshot
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            // @todo remove previous final snapshot ... expiry policy?
            // @todo remove temp snapshot
        }
    }
}
