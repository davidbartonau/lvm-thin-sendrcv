package oneit.lvmsendrcv;

import java.io.*;
import java.sql.*;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.xpath.XPathExpressionException;
import org.apache.commons.cli.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 
 * @author david
 */
public class DDRandomReceive 
{
    public final long   blockSizeBytes;
    public final String outputFile;

    
    public DDRandomReceive(long blockSizeBytes, String outputFile) 
    {
        this.blockSizeBytes = blockSizeBytes;
        this.outputFile = outputFile;
    }
    
    
    public void writeData () throws IOException, SAXException, ParserConfigurationException, XPathExpressionException
    {
        SAXParserFactory        factory = SAXParserFactory.newInstance();
        SAXParser               saxParser = factory.newSAXParser();

        try (RandomAccessFile       outfileRW = new RandomAccessFile(outputFile, "rw"))
        {
            DDReceiveSAXHandler handler = new DDReceiveSAXHandler(outfileRW);
            
            saxParser.parse(System.in, handler);
            
            System.err.println("DDRandomReceive: Written " + handler.blocksWritten + " blocks");
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
    
    
    public class DDReceiveSAXHandler extends DefaultHandler
    {
        RandomAccessFile    outfileRW;
        StringBuilder       blockBuffer = new StringBuilder();
        long                blockOffset;
        int                 blocksWritten = 0;
        
        
        private DDReceiveSAXHandler(RandomAccessFile outfileRW) 
        {
            this.outfileRW= outfileRW;
        }

        
        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException 
        {
            if (qName.equals("block"))
            {
                blockBuffer = new StringBuilder();
                blockOffset = blockSizeBytes * Integer.parseInt(attributes.getValue("offset"));
            }
        }

        
        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException 
        {
            if (qName.equals("block"))
            {
                String  blockContent = blockBuffer.toString();
                byte[]  blockBytes = Base64.getDecoder().decode(blockContent);

                assert blockBytes.length == blockSizeBytes : "Block size does not match";

                System.err.println("DDRandomReceive: Receive Block " + blockOffset + ":" + blockBytes.length); // @todo use proper logging
                
                try
                {
                    outfileRW.seek(blockOffset);
                    outfileRW.write(blockBytes);
                    blocksWritten++;
                }
                catch(IOException e)
                {
                    System.err.println("Unable to write file:" + e.getMessage());
                    System.exit (1);
                }
                
                blockBuffer = new StringBuilder();
            }
        }

        
        @Override
        public void characters(char[] ch, int start, int length) throws SAXException 
        {
            blockBuffer.append(new String(ch, start, length));
        }

    }
}
