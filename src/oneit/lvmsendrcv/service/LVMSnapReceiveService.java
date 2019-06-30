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
public class LVMSnapReceiveService extends Thread
{
    private final Socket socket;
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) 
    {
        try
        {
            ServerSocket    svrSocket = new ServerSocket(15432, 10, Inet4Address.getLocalHost());

            while (true)
            {
                Socket  socket = svrSocket.accept();
                
                new LVMSnapReceiveService(socket).start();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(1);
        }
    }

    
    public LVMSnapReceiveService(Socket socket) 
    {
        this.socket = socket;
    }

    
    @Override
    public void run() 
    {
        try
        {
            BufferedInputStream     in = new BufferedInputStream(socket.getInputStream());
            OutputStream            out = socket.getOutputStream();

            System.err.println("Ready to receive:" + in);
            LVMSnapReceive.receiveSnapshot(in, out);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            try
            {
                socket.close();
            } 
            catch (IOException e) 
            {
                e.printStackTrace();
            }
        }
    }
}
