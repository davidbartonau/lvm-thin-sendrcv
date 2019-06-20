package oneit.lvmsendrcv;

import java.io.*;

/**
 *
 * @author david
 */
public class IOUtils 
{
    public static final int             BUFFER_SIZE = 4096;
    
    
    public static void pump (InputStream in, OutputStream out) throws IOException
    {
        pump(in, out, Long.MAX_VALUE);
    }
    
    
    public static void pump (InputStream in, OutputStream out, long maxBytes) throws IOException
    {
        if (maxBytes <= 0)
        {
            return;
        }
        
        byte[]  buffer = new byte[BUFFER_SIZE];
        int     bytesRead;
        long    totalBytesRead = 0;
        int     maxBytesToRead = (int)Math.min(maxBytes, BUFFER_SIZE);

        while (totalBytesRead < maxBytes && (bytesRead = in.read(buffer, 0, maxBytesToRead)) >= 0)
        {
            out.write(buffer, 0, bytesRead);
            totalBytesRead += bytesRead;
            maxBytesToRead = (int)Math.min(maxBytes - totalBytesRead, BUFFER_SIZE);
        }
    }
    
    
    public static class OutputPusher implements Runnable
    {
        private OutputStream o;
        private InputStream i;

        public OutputPusher (InputStream i, OutputStream o)
        {
            this.o = o;
            this.i = i;
        }


        public void run ()
        {
            try
            {
                IOUtils.pump(i, o);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }
    
}
