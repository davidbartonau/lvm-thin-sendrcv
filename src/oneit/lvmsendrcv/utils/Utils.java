package oneit.lvmsendrcv.utils;

import java.io.*;
import java.util.Enumeration;
import java.util.Properties;
import javax.mail.*;  
import javax.mail.internet.*;  
//import javax.activation.*;  
import org.apache.commons.cli.*;

/**
 *
 * @author david
 */
public class Utils 
{
    public static final Properties CONFIG_PROPERTIES = new Properties();
    public static final Properties JAVA_MAIL_PROPERTIES;
    
    static 
    {
        File    configFile = new File ("/etc/lvmsendrcv/lvmsendrcv.conf");
        
        if (configFile.exists() && configFile.canRead())
        {
            try (FileReader configReader = new FileReader(configFile))
            {
                CONFIG_PROPERTIES.load(configReader);
            }
            catch (IOException e)
            {
                System.err.println("Cannot load config:" + configFile); // @todo
            }
        }
        
        JAVA_MAIL_PROPERTIES = getPropertiesOf(CONFIG_PROPERTIES, "email.");
    }
    
    

    /**
     * Returns a Property set consisting of all Properties in source that start with a specified prefix.
     * The properties returned will have prefix removed from the key.
     */
    public static Properties getPropertiesOf (Properties source, String prefix)
    {
        Properties result = new Properties ();

        for (String propertyName : source.stringPropertyNames() )
        {
            if (propertyName.startsWith (prefix))
            {
                result.put (propertyName.substring (prefix.length ()), source.getProperty (propertyName));
            }
        }

        return result;
    }
    
    
    public static String getMandatoryString (String arg, CommandLine  cmdLine, Options options)
    {
        String  param = cmdLine.getOptionValue(arg);
        
        if (param == null)
        {
            Option  opt = options.getOption(arg);
            
            throw new RuntimeException("Missing param:" + arg + " " + opt.getOpt() + "/" + opt.getLongOpt() + " :" + opt.getDescription());            
        }
        else
        {
            return param;
        }
    }
    
    
    public static void sendEmail (String subject, String messageText)
    {
        Session session = Session.getDefaultInstance(JAVA_MAIL_PROPERTIES);  
        
        try
        {  
            MimeMessage message = new MimeMessage(session);  
            message.setFrom(new InternetAddress("dave@oneit.com.au"));  
            message.addRecipient(Message.RecipientType.TO,new InternetAddress("dave@oneit.com.au"));  
            message.setSubject(subject);  
            message.setText(messageText);  

            // Send message  
            Transport.send(message);  
        }
        catch (MessagingException mex) 
        {
            throw new RuntimeException(mex);
        }  
    }
    
    public static void main (String[] args)
    {
        sendEmail(args[0], args[1]);
    }
}
