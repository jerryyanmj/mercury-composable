/*

    Copyright 2018-2025 Accenture Technology

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

 */

package org.platformlambda.core;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlParserFeatureTest {
    private static final Logger log = LoggerFactory.getLogger(XmlParserFeatureTest.class);

    /*
     * Acknowledgement:
     *
     * The solution to avoid XML external entity (XXE) injection attack was contributed by Sajeeb Lohani
     * (prodigysml and n33dle) on 1/6/2020.
     *
     * This test ensures that the FEATURES_TO_ENABLE and FEATURES_TO_DISABLE are enforced.
     */
    private static final String[] FEATURES_TO_ENABLE = {
            "http://apache.org/xml/features/disallow-doctype-decl"
    };
    private static final String[] FEATURES_TO_DISABLE = {
            /*
             * Features not recognized by the default DocumentBuilderFactory:
             * http://xerces.apache.org/xerces-j/features.html#external-general-entities
             * http://xerces.apache.org/xerces2-j/features.html#external-general-entities
             * http://xerces.apache.org/xerces-j/features.html#external-parameter-entities
             * http://xerces.apache.org/xerces2-j/features.html#external-parameter-entities
             */
            "http://xml.org/sax/features/external-general-entities",
            "http://xml.org/sax/features/external-parameter-entities",
            "http://apache.org/xml/features/nonvalidating/load-external-dtd"
    };

    @Test
    void featureTest() throws ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        for (String feature: FEATURES_TO_ENABLE) {
            assertTrue(setFeature(dbf, feature, true));
        }
        for (String feature: FEATURES_TO_DISABLE) {
            assertTrue(setFeature(dbf, feature, false));
        }
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        /*
         * Guarantee that DOCTYPE feature is disabled
         * XML sample from https://portswigger.net/web-security/xxe
         */
        String problematic = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                             "<!DOCTYPE foo [ <!ENTITY xxe SYSTEM \"file:///etc/passwd\"> ]>\n" +
                             "<stockCheck><productId>&xxe;</productId></stockCheck>";
        DocumentBuilder dBuilder = dbf.newDocumentBuilder();
        dBuilder.setErrorHandler(null);
        SAXParseException ex = assertThrows(SAXParseException.class,
                () -> dBuilder.parse(new ByteArrayInputStream(problematic.getBytes(StandardCharsets.UTF_8))));
        assertTrue(ex.getMessage().contains("DOCTYPE is disallowed"));
    }

    private boolean setFeature(DocumentBuilderFactory dbf, String feature, boolean enable) {
        try {
            dbf.setFeature(feature, enable);
            return dbf.getFeature(feature) == enable;
        } catch (ParserConfigurationException e) {
            log.error("Unable to {} feature - {}", enable? "enable" : "disable", e.getMessage());
            return false;
        }
    }

}
