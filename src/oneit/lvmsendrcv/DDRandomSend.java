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
public class DDRandomSend 
{
    public final int    blockSizeBytes;
    public final String inputFile;
    public final int[]  blocks;

    
    public DDRandomSend(int blockSizeBytes, String inputFile, int[] blocks) 
    {
        this.blockSizeBytes = blockSizeBytes;
        this.inputFile = inputFile;
        this.blocks = blocks;
    }

   
        
    public void readData () throws IOException, SAXException, ParserConfigurationException, XPathExpressionException
    {
        try (RandomAccessFile       infileRO = new RandomAccessFile(inputFile, "r"))
        {
            byte[]  blockbuffer = new byte[blockSizeBytes];

            System.out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n<dd>");

            for (long block : blocks)
            {
                System.err.println("Process block:" + block + " -> " + (block * blockSizeBytes)); // @todo
                infileRO.seek(block * blockSizeBytes);
                infileRO.readFully(blockbuffer);    // @todo what if there are not enough bytes left?
                
                System.out.print("<block offset='" + block + "'>");
                System.out.print(Base64.getEncoder().encodeToString(blockbuffer));
                System.out.print("</block>");
                System.out.println();
            }

            System.out.println("</dd>");
        }
    }
    

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) 
    {
        try
        {
            Options             options = new Options();
            CommandLineParser   parser = new GnuParser();

            options.addOption("if", "inputFile", true, "The file or device to write to.");
            options.addOption("bs", "blockSize", true, "The block size in kB.");
            options.addOption("blocks", true, "Comma separated list of blocks to read e.g. 5,7,31");
            options.addOption("h", "help", false, "Print usage help.");

            CommandLine  cmdLine = parser.parse(options, args);

            if (cmdLine.hasOption("h"))
            {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "oneit.lvmsendrcv.DDRandomSend", 
                                     "Writes a random selection of blocks from a device or file to stdout.  Intended to act as the input of DDRandomReceive.", 
                                     options,
                                     "XML is sent to stout and is of the format\n<dd>\n  <block offset='5'>base64 encoded bytes</block>");
                return;
            }
            else
            {
                String          inf = cmdLine.getOptionValue("if");
                int             bs = 1024 * Integer.parseInt(cmdLine.getOptionValue("bs"));
                String          blocksStr = cmdLine.getOptionValue("blocks");
                String[]        blocksStrArr = blocksStr.split(",");
                int[]           blocks = new int[blocksStrArr.length];
                
                for (int x = 0 ; x < blocksStrArr.length ; ++x)
                {
                    blocks[x] = Integer.parseInt(blocksStrArr[x]);
                }
                
                DDRandomSend    ddRandomSend = new DDRandomSend(bs, inf, blocks);
                
                ddRandomSend.readData();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(1);
        }
    }   
}
