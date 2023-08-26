package com.michaelrthon.geneiousplugins.interproscan;

import com.biomatters.geneious.publicapi.documents.sequence.SequenceAnnotation;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAnnotationInterval;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAnnotationInterval.Direction;
import com.biomatters.geneious.publicapi.plugin.SequenceAnnotationGenerator.AnnotationGeneratorResult;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class XmlParser {

    private Document doc = null;
    private String featType = null;
    private boolean makeExtraFeats = true;
    private int seqLength = 0;

    public XmlParser(String xmlText) {
        if (xmlText != null) {
            //System.out.println(xmlText);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = null;
            try {
                builder = factory.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                e.printStackTrace();
            }
            InputStream is;
            is = new ByteArrayInputStream(xmlText.getBytes(StandardCharsets.UTF_8));

            try {
                if (builder != null) {
                    doc = builder.parse(is);
                }
            } catch (SAXException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    public AnnotationGeneratorResult parseXml() {

        AnnotationGeneratorResult result = new AnnotationGeneratorResult();

        if (doc == null) {
            if (makeExtraFeats) {
                SequenceAnnotation errorAnnotation = new SequenceAnnotation("InterProScan Error", "InterPro Term");
                SequenceAnnotationInterval interval = new SequenceAnnotationInterval(1, seqLength, Direction.none);
                errorAnnotation.addInterval(interval);
                result.addAnnotationToAdd(errorAnnotation);
            }
        } else {
            NodeList protList = evalXpath("//protein", doc);
            // if the web servce returns an empty xml document (or no xml document?)
            // then protLsit should have length 0
            // this occurs when there are no search results for the protein
            if (protList.getLength() > 0) {
                Node prot = protList.item(0);
                //String protLen = prot.getAttributes().getNamedItem("length").getNodeValue();

                NodeList nodes = evalXpath("//matches/*", doc);
                for (int i = 0; i < nodes.getLength(); i++) {
                    Node match = nodes.item(i);
                    //NamedNodeMap attrs = node.getAttributes();
                    //String iprId = attrs.getNamedItem("id").getNodeValue();

                    NodeList sigList = evalXpath("signature", match);
                    Node signature = sigList.item(0);
                    NamedNodeMap sigAttrs = signature.getAttributes();
                    String matchId = sigAttrs.getNamedItem("ac").getNodeValue();
                    String matchName;
                    Node matchNameNode = sigAttrs.getNamedItem("name");
                    if (matchNameNode != null) {
                        matchName = matchNameNode.getNodeValue();//still error here
                    } else {
                        matchName = "unknown";
                    }
                    //String matchDesc = matchDesc = sigAttrs.getNamedItem("desc").getNodeValue();

                    //note: a signature won't have an 'entry' node if the match has not
                    // yet been incorportated into the interpro db.
                    String iprId;
                    String iprName;
                    String iprType;

                    NodeList entryList = evalXpath("entry", signature);
                    Node entryNode = entryList.item(0);
                    if (entryNode == null) {
                        iprId = "";
                        iprName = "Unintegrated";
                        iprType = "Unintegrated";
                    } else {
                        NamedNodeMap entryAttrs = entryNode.getAttributes();
                        iprId = entryAttrs.getNamedItem("ac").getNodeValue();
                        iprName = entryAttrs.getNamedItem("name").getNodeValue();
                        iprType = entryAttrs.getNamedItem("type").getNodeValue();
                    }
                    NodeList libNodeList = evalXpath("signature-library-release", signature);
                    Node libNode = libNodeList.item(0);
                    NamedNodeMap libAttrs = libNode.getAttributes();
                    String matchDbName = libAttrs.getNamedItem("library").getNodeValue();

                    SequenceAnnotation annotation = new SequenceAnnotation(matchName, prettyStringForDbName(matchDbName));
                    annotation.addQualifier("Database", matchDbName);
                    annotation.addQualifier("Id", matchId);
                    annotation.addQualifier("Name", matchName);
                    //annotation.addQualifier("Description", matchDesc);

                    if (featType.equals(InterproscanOptions.FEAT_QUALIFIERS)) {
                        annotation.addQualifier("InterPro ID", "<a href=\"http://www.ebi.ac.uk/interpro/entry/" + iprId + "\">" + iprId + "</a>");
                        annotation.addQualifier("InterPro Name", iprName);
                        annotation.addQualifier("InterPro Type", iprType);
                    }

                    NodeList locations = evalXpath("locations/*", match);
                    for (int l = 0; l < locations.getLength(); l++) {
                        Node location = locations.item(l);
                        NamedNodeMap locAttrs = location.getAttributes();
                        int locStart = Integer.parseInt(locAttrs.getNamedItem("start").getNodeValue());
                        int locEnd = Integer.parseInt(locAttrs.getNamedItem("end").getNodeValue());
                        SequenceAnnotationInterval interval = new SequenceAnnotationInterval(locStart, locEnd, Direction.none);
                        annotation.addInterval(interval);
                    }
                    result.addAnnotationToAdd(annotation);

                    if (featType.equals(InterproscanOptions.FEAT_SEPARATE)) {
                        SequenceAnnotation iprAnnotation = new SequenceAnnotation(iprName, "InterPro Term");
                        iprAnnotation.addQualifier("id", "<a href=\"http://www.ebi.ac.uk/interpro/entry/" + iprId + "\">" + iprId + "</a>");
                        iprAnnotation.addQualifier("type", iprType);
                        iprAnnotation.addInterval(1, seqLength, Direction.none);
                        result.addAnnotationToAdd(iprAnnotation);
                    }

                }
            } else {
                if (makeExtraFeats) {
                    SequenceAnnotation errorAnnotation = new SequenceAnnotation("No InterProScan Results", "InterPro Term");
                    SequenceAnnotationInterval interval = new SequenceAnnotationInterval(1, seqLength, Direction.none);
                    errorAnnotation.addInterval(interval);
                    result.addAnnotationToAdd(errorAnnotation);
                }
            }
        }
        return result;
    }

    private String prettyStringForDbName(String dbName) {
        String prettyString = dbName;

        if (dbName.equals("SIGNALP")) {
            prettyString = "SignalP";
        }
        if (dbName.equals("PFAM")) {
            prettyString = "Pfam";
        }
        if (dbName.equals("SUPERFAMILY")) {
            prettyString = "Superfamily";
        }
        if (dbName.equals("GENE3D")) {
            prettyString = "Gene3D";
        }
        if (dbName.equals("PRODOM")) {
            prettyString = "ProDom";
        }
        if (dbName.equals("PANTHER")) {
            prettyString = "Panther";
        }

        return prettyString;
    }

    private NodeList evalXpath(String path, Object obj) {
        NodeList nodes = null;
        XPathFactory xFactory = XPathFactory.newInstance();
        XPath xpath = xFactory.newXPath();
        XPathExpression expr = null;
        try {
            expr = xpath.compile(path);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }

        try {
            if (expr != null) {
                nodes = (NodeList)expr.evaluate(obj, XPathConstants.NODESET);
            }
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }

        return nodes;
    }

    /**
     * @param featType the featType to set
     */
    public XmlParser setFeatType(String featType) {
        this.featType = featType;
        return this;
    }

    /**
     * @param makeExtraFeats the makeExtraFeats to set
     */
    public XmlParser setMakeExtraFeats(boolean makeExtraFeats) {
        this.makeExtraFeats = makeExtraFeats;
        return this;
    }

    /**
     * @param seqLength the length of the sequence being parsed
     */
    public XmlParser setSeqLength(int seqLength) {
        this.seqLength = seqLength;
        return this;
    }
}
