package uk.nhs.adaptors.gp2gp.common.service;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

@Component
public class XPathService {
    public Document parseDocumentFromXml(String xml) throws SAXException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        InputSource inputSource;
        DocumentBuilder documentBuilder;
        try {
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
            inputSource = new InputSource(new StringReader(xml));
            return documentBuilder.parse(inputSource);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Unable to configure XML parser", e);
        } catch (IOException e) {
            throw new RuntimeException("IO error while reading XML", e);
        }
    }

    public String getNodeValue(Document xmlDoc, String expression) {
        try {
            XPathExpression xPathExpression = XPathFactory.newInstance()
                .newXPath()
                .compile(expression);
            return (String) xPathExpression.evaluate(xmlDoc, XPathConstants.STRING);
        } catch (XPathExpressionException e) {
            throw new IllegalArgumentException("Invalid xpath expression " + expression, e);
        }
    }

    public String getNodeValue(Document xmlDoc, String firstExpression, String secondExpression) {
        var firstExtraction = getNodeValue(xmlDoc, firstExpression);
        if (StringUtils.isNotBlank(firstExtraction)) {
            return firstExtraction;
        } else {
            return getNodeValue(xmlDoc, secondExpression);
        }
    }
}
