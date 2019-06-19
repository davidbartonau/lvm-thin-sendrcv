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
public class DDRandomReceive 
{
    public final int    blockSizeBytes;
    public final String outputFile;

    
    public DDRandomReceive(int blockSizeBytes, String outputFile) 
    {
        this.blockSizeBytes = blockSizeBytes;
        this.outputFile = outputFile;
    }
    
    
    public void writeData () throws IOException, SAXException, ParserConfigurationException, XPathExpressionException
    {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder        dBuilder = dbFactory.newDocumentBuilder();        
        Document               doc = dBuilder.parse(System.in);

        try (RandomAccessFile       outfileRW = new RandomAccessFile(outputFile, "rw"))
        {
            for (Node blockNode : Utils.getXPathNodes(doc, "dd/block"))
            {
                int     blockOffset = blockSizeBytes * Integer.parseInt(Utils.getXPathVal(blockNode, "@offset"));
                String  blockContent = Utils.getXPathVal(blockNode, "text()");
                byte[]  blockBytes = Base64.getDecoder().decode(blockContent);

                // @todo assert blockBytes.length == blockSizeBytes : "Block size does not match";

                System.out.println("Block " + blockOffset + ":" + blockBytes.length); // @todo use proper logging
                outfileRW.seek(blockOffset);
                outfileRW.write(blockBytes);
            }
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

            options.addOption("of", "outputFile", true, "The file or device to write to.");
            options.addOption("bs", "blockSize", true, "The block size in kB.");
            options.addOption("v", "verbose", false, "Be verbose.");
            options.addOption("h", "help", false, "Print usage help.");

            CommandLine  cmdLine = parser.parse(options, args);

            if (cmdLine.hasOption("h"))
            {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "oneit.lvmsendrcv.DDRandomReceive", 
                                     "Takes an XML sequence of blocks and writes them to a device / file.  Intended to take the output of DDRandomSend.  Buffers all blocks in memory, so be cautious.", 
                                     options,
                                     "XML is sent to stdin and is of the format\n<dd>\n  <block offset='5'>base64 encoded bytes</block>");
                return;
            }
            else
            {
                String          of = cmdLine.getOptionValue("of");
                int             bs = 1024 * Integer.parseInt(cmdLine.getOptionValue("bs"));
                DDRandomReceive ddRandomReceive = new DDRandomReceive(bs, of);
                
                ddRandomReceive.writeData();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(1);
        }
    }   
}
