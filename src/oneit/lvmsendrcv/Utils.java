/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package oneit.lvmsendrcv;

import java.util.*;
import javax.xml.xpath.*;
import org.w3c.dom.*;


/**
 *
 * @author david
 */
public class Utils 
{
    public static final XPathFactory    XPATH_FACTORY = XPathFactory.newInstance();
    
    
    public static String getXPathVal(Node node, String xpath) throws XPathExpressionException
    {
        XPath           xpathObj = XPATH_FACTORY.newXPath();
            
        return xpathObj.evaluate(xpath, node);
    }

    
    public static List<Node> getXPathNodes (Node node, String xpath) throws XPathExpressionException
    {
        XPath           xpathObj = XPATH_FACTORY.newXPath();
        NodeList        nodes = (NodeList)xpathObj.evaluate(xpath, node, XPathConstants.NODESET);
        List<Node>      result = new ArrayList<Node>();

        if (nodes != null)
        {
            for(int i=0 ; i < nodes.getLength() ; i++)
            {
                result.add(nodes.item(i));
            }
        }

        return result;
    }
    
}
