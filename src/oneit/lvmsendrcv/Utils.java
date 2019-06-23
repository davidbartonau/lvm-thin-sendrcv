package oneit.lvmsendrcv;

import org.apache.commons.cli.*;

/**
 *
 * @author david
 */
public class Utils 
{
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
}
