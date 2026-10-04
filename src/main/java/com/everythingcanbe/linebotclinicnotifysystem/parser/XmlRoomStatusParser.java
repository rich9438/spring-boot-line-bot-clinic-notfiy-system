package com.everythingcanbe.linebotclinicnotifysystem.parser;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.helpers.DefaultHandler;

import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomNames;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;

/**
 * 將診間 XML（{@code <RB><Datas><RoomInfo .../></Datas></RB>}）轉換為 {@link RoomStatus}。
 */
@Component
public class XmlRoomStatusParser {

    private static final DateTimeFormatter EXEC_TIME_FORMAT = DateTimeFormatter.ofPattern("uuuuMMddHHmmssSSS");
    private static final String SUCCESS_CODE = "0000";

    private final Clock clock;
    private final DocumentBuilderFactory factory;

    public XmlRoomStatusParser(Clock clock) {
        this.clock = clock;
        this.factory = createSecureFactory();
    }

    public RoomStatus parse(byte[] content, String providerCode) {
        Document document = readDocument(stripBom(content));

        Element ret = firstElement(document, "RET");
        if (ret != null && !SUCCESS_CODE.equals(ret.getAttribute("RETCODE"))) {
            throw new XmlParseException("Unexpected RETCODE: " + ret.getAttribute("RETCODE"));
        }
        Element room = firstElement(document, "RoomInfo");
        if (room == null) {
            throw new XmlParseException("RoomInfo element not found");
        }

        Integer roomId = parseInteger(room.getAttribute("Title2"));
        if (roomId == null) {
            throw new XmlParseException("Invalid room id (Title2): " + room.getAttribute("Title2"));
        }
        Integer number = parseInteger(room.getAttribute("Number1"));
        LocalDateTime updateTime = parseExecTime(room.getAttribute("EXECTIME1"));
        LocalDate today = LocalDate.now(clock);
        boolean inSession = number != null && number > 0
                && updateTime != null && updateTime.toLocalDate().equals(today);

        return new RoomStatus(
                providerCode,
                roomId,
                RoomNames.of(roomId),
                blankToNull(room.getAttribute("DOC1")),
                blankToNull(room.getAttribute("Title1")),
                inSession ? number : null,
                inSession,
                updateTime);
    }

    private Document readDocument(byte[] content) {
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            // DefaultHandler：fatal error 直接拋出，不輸出到 stderr
            builder.setErrorHandler(new DefaultHandler());
            return builder.parse(new ByteArrayInputStream(content));
        } catch (Exception e) {
            throw new XmlParseException("Failed to parse room XML", e);
        }
    }

    private static Element firstElement(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    public static byte[] stripBom(byte[] content) {
        if (content.length >= 3
                && (content[0] & 0xFF) == 0xEF && (content[1] & 0xFF) == 0xBB && (content[2] & 0xFF) == 0xBF) {
            byte[] stripped = new byte[content.length - 3];
            System.arraycopy(content, 3, stripped, 0, stripped.length);
            return stripped;
        }
        return content;
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime parseExecTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), EXEC_TIME_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static DocumentBuilderFactory createSecureFactory() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure XML parser", e);
        }
    }

}
