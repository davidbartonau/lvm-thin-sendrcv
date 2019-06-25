package oneit.lvmsendrcv.utils;

import java.util.*;
import java.io.*;
import javax.xml.xpath.*;
import org.w3c.dom.*;


/**
 *
 * @author david
 */
public class ExecUtils 
{
    public static Thread run(Runnable r, int priority, boolean isDaemon)
    {
        Thread  newThread = new Thread (r);
		
        newThread.setPriority(priority);
        newThread.setDaemon(isDaemon);
        newThread.start();

        return newThread;
    }
    
    public static class ExecuteProcess 
    {
        private String      homeDirectory;
        private String[]    command;
        private boolean     hideStdOut = false; // Hides stdout unless there is an error
        
        public ExecuteProcess (String homeDirectory, String... command)
        {
            this.homeDirectory = homeDirectory;
            this.command = command;
        }

        
        public ExecuteProcess setHideStdOut(boolean hideStdOut)
        {
            this.hideStdOut = hideStdOut;
            return this;
        }
        
        
        public List<String> executeAsLines () throws InterruptedException, IOException
        {
            String      output = executeAsBytes().toString();
            String[]    outputLines = output.split("\n");            

            return Arrays.asList(outputLines);
        }
        
        
        public byte[] executeAsBytes () throws InterruptedException, IOException
        {
            
            //System.err.print("Executing:" + Arrays.asList(command)); // @todo log

            ProcessBuilder          pb = new ProcessBuilder(command).directory(new File (homeDirectory)).redirectInput(ProcessBuilder.Redirect.INHERIT);

            //pb.environment().putAll(EXEC_ENV);
            
            Process                 p = pb.start();       
            ByteArrayOutputStream   stdoutBuffer = new ByteArrayOutputStream();
            ByteArrayOutputStream   stderrBuffer = new ByteArrayOutputStream();
            OutputStream            out = hideStdOut ? stdoutBuffer : new TeeOutputStream(stdoutBuffer, System.out);

            // This writes stdout and stderr from the process to our own stdout and stderr respectively
            Thread  t1 = ExecUtils.run(new IOUtils.OutputPusher (p.getInputStream(), out), Thread.NORM_PRIORITY, true);
            Thread  t2 = ExecUtils.run(new IOUtils.OutputPusher (p.getErrorStream(), new TeeOutputStream(stderrBuffer, System.err)), Thread.NORM_PRIORITY, true);

            int result = p.waitFor();
            
            t1.join();
            t2.join();

            if (result == 0)
            {
                return stdoutBuffer.toByteArray();
            }
            else
            {
                throw new RuntimeException("Eror running:" + Arrays.asList(command));
            }
        }        
    }
    
    public static class TeeOutputStream extends OutputStream
    {
        OutputStream[]  targets;

        
        public TeeOutputStream(OutputStream... targets)
        {
            this.targets = targets;
        }
        
        
        @Override
        public void write(int b) throws IOException
        {
            for (OutputStream target : targets)
            {
                target.write(b);
            }
        }

        
        @Override
        public void flush() throws IOException
        {
            for (OutputStream target : targets)
            {
                target.flush();
            }
        }

        
        @Override
        public void close() throws IOException
        {
            for (OutputStream target : targets)
            {
                target.close();
            }
        }
    }
}
