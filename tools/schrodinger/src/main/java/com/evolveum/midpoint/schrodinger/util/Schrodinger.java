package com.evolveum.midpoint.schrodinger.util;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;

import javax.xml.namespace.QName;

/**
 * Created by Viliam Repan (lazyman).
 */
public class Schrodinger {

    public static final String SCHRODINGER_ELEMENT = "schrodinger";

    public static final String DATA_S_RESOURCE_KEY = "data-s-resource-key";
    public static final String DATA_S_ID = "data-s-id";
    public static final String DATA_S_QNAME = "data-s-qname";

    public static By byDataResourceKey(String key) {
        return byDataResourceKey(null, key);
    }

    public static By bySchrodingerDataResourceKey(String key) {
        return byDataResourceKey(SCHRODINGER_ELEMENT, key);
    }

    public static By byDataResourceKey(String elementName, String key) {
        if (elementName == null) {
            elementName = "*";
        }

        return byElementAttributeValue(elementName, DATA_S_RESOURCE_KEY, key);
    }

    public static By byDataId(String id) {
        return byDataId(null, id);
    }

    public static By bySchrodingerDataId(String id) {
        return byDataId(SCHRODINGER_ELEMENT, id);
    }

    public static By byDataId(String elementName, String id) {
        return byElementAttributeValue(elementName, DATA_S_ID, id);
    }

    public static By byDataQName(String qname) {
        return byDataQName(null, qname);
    }

    public static By bySchrodingerDataQName(String qname) {
        return byDataQName(SCHRODINGER_ELEMENT, qname);
    }

    public static By byDataQName(String elementName, String qname) {
        return byElementAttributeValue(elementName, DATA_S_QNAME, qname);
    }

    public static By byElementAttributeValue(String element, String attr, String value) {
        if (element == null) {
            element = "*";
        }
        return By.xpath("//" + element + "[@" + attr + "='" + value + "']");
    }

    public static By byElementAttributeValue(String element, String function, String attr, String value) {
        if (element == null) {
            element = "*";
        }

        return By.xpath("//" + element + "[" + function + "(@" + attr + ",'" + value + "')]");
    }

    public static By byElementValue(String elementName, String value) {
        if (elementName == null) {
            elementName = "*";
        }

        return By.xpath("//" + elementName + "[text()='" + value + "']");
    }

    public static String qnameToString(QName qname) {
        if (qname == null) {
            return null;
        }

        return StringUtils.join(new Object[]{qname.getNamespaceURI(), qname.getLocalPart()}, "#");
    }
}
