package de.seuhd.campuscoffee.data.impl;

import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * OSM import service communicating with the OpenStreetMap API.
 */
@Service
@Slf4j
class OsmDataServiceImpl implements OsmDataService {
    static final String DEFAULT_OSM_BASE_URL = "https://www.openstreetmap.org/api/0.6";
    private final HttpClient httpClient;

    OsmDataServiceImpl() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    OsmDataServiceImpl(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public @NonNull OsmNode fetchNode(@NonNull Long nodeId) throws OsmNodeNotFoundException {
        if (nodeId <= 0) {
            throw new IllegalArgumentException("The OpenStreetMap node ID must be positive.");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DEFAULT_OSM_BASE_URL + "/node/" + nodeId))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/xml")
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while fetching OSM node " + nodeId, e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch OSM node " + nodeId, e);
        }

        int status = response.statusCode();
        if (status == 404) {
            throw new OsmNodeNotFoundException(nodeId);
        }
        if (status < 200 || status >= 300) {
            throw new RuntimeException("Unexpected status " + status + " while fetching OSM node " + nodeId);
        }

        try (InputStream body = response.body()) {
            return parseNode(nodeId, body);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read OSM node response for " + nodeId, e);
        }
    }

    private OsmNode parseNode(Long nodeId, InputStream body) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setExpandEntityReferences(false);
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (ParserConfigurationException | IllegalArgumentException ignored) {
            log.debug("Unable to apply secure XML parser features, continuing with defaults");
        }

        Document document;
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            document = builder.parse(body);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException("Failed to parse OSM node " + nodeId, e);
        }

        NodeList nodeList = document.getElementsByTagName("node");
        if (nodeList.getLength() == 0) {
            throw new OsmNodeNotFoundException(nodeId);
        }

        Element nodeElement = (Element) nodeList.item(0);
        Double latitude = parseDoubleAttribute(nodeElement, "lat");
        Double longitude = parseDoubleAttribute(nodeElement, "lon");

        Map<String, String> tags = extractTags(nodeElement);

    return OsmNode.builder()
                .nodeId(nodeId)
                .name(firstNonBlank(tags, "name", "name:en", "name:de"))
                .amenity(firstNonBlank(tags, "amenity"))
                .description(firstNonBlank(tags, "description", "note"))
                .latitude(latitude)
                .longitude(longitude)
                .street(firstNonBlank(tags, "addr:street"))
                .houseNumber(firstNonBlank(tags, "addr:housenumber"))
                .postalCode(firstNonBlank(tags, "addr:postcode"))
                .city(firstNonBlank(tags, "addr:city"))
                .openingHours(firstNonBlank(tags, "opening_hours"))
                .phone(firstNonBlank(tags, "phone", "contact:phone"))
                .website(firstNonBlank(tags, "website", "contact:website"))
                .shop(firstNonBlank(tags, "shop"))
                .build();
    }

    private static Map<String, String> extractTags(Element nodeElement) {
        Map<String, String> tags = new HashMap<>();
        NodeList tagNodes = nodeElement.getElementsByTagName("tag");
        for (int i = 0; i < tagNodes.getLength(); i++) {
            Element tagElement = (Element) tagNodes.item(i);
            String key = tagElement.getAttribute("k");
            String value = tagElement.getAttribute("v");
            if (!key.isEmpty()) {
                tags.put(key, value);
            }
        }
        return tags;
    }

    private static Double parseDoubleAttribute(Element element, String attributeName) {
        String rawValue = element.getAttribute(attributeName);
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(rawValue);
        } catch (NumberFormatException e) {
            log.warn("Unable to parse '{}' attribute '{}' as double", attributeName, rawValue);
            return null;
        }
    }

    private static String firstNonBlank(Map<String, String> tags, String... keys) {
        for (String key : keys) {
            String value = tags.get(key);
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return null;
    }
}
