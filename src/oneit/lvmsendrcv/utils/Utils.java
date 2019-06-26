package oneit.lvmsendrcv.utils;

import java.io.*;
import java.util.*;
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
     * Returns the unique property names up to the delimiting character
     * a.foo
     * a.bar
     * b.baz
     * In the example above, if the delimiting character is . then a and b would be returned
     * 
     * @param source the source properties
     * @param delimiter the delimiter for this group.  The group is taken to the first occurence of the delimiter.
     * @param includeNoDelimiter should the result include properties that do not include the delimiter
     */
    public static Set<String> getPropertyGroups (Properties source, char delimiter, boolean includeNoDelimiter)
    {
        Set<String>     result = new HashSet<>();
        
        for (String propertyName : source.stringPropertyNames() )
        {
            int delimIndex = propertyName.indexOf(delimiter);
            
            if (delimIndex >= 0)
            {
                result.add(propertyName.substring(0, delimIndex));
            }
            else if (includeNoDelimiter)
            {
                result.add(propertyName);
            }
        }

        return result;
        
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
        System.err.println("Java Mail Properties:" + JAVA_MAIL_PROPERTIES); // @todo
        System.err.println("Config Properties:" + CONFIG_PROPERTIES); // @todo
        
        sendEmail(args[0], args[1]);
    }
}
