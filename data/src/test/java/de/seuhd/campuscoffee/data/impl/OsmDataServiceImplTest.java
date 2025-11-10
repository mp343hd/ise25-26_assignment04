package de.seuhd.campuscoffee.data.impl;

import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OsmDataServiceImplTest {

    private static final String SAMPLE_XML = """
            <osm version=\"0.6\">
              <node id=\"5589879349\" lat=\"49.4122362\" lon=\"8.7077883\">
                <tag k=\"name\" v=\"Rada Coffee &amp; Rösterei\"/>
                <tag k=\"amenity\" v=\"cafe\"/>
                <tag k=\"description\" v=\"Caffé und Rösterei\"/>
                <tag k=\"addr:street\" v=\"Untere Straße\"/>
                <tag k=\"addr:housenumber\" v=\"21\"/>
                <tag k=\"addr:postcode\" v=\"69117\"/>
                <tag k=\"addr:city\" v=\"Heidelberg\"/>
                <tag k=\"opening_hours\" v=\"Mo-Fr 11:00-18:00\"/>
                <tag k=\"phone\" v=\"+49 6221 1805585\"/>
                <tag k=\"website\" v=\"https://rada-roesterei.com/\"/>
              </node>
            </osm>
            """;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<InputStream> httpResponse;

    @Captor
    private ArgumentCaptor<HttpRequest> requestCaptor;

    private OsmDataServiceImpl dataService;

    @BeforeEach
    void setUp() {
        dataService = new OsmDataServiceImpl(httpClient);
    }

    @Test
    void fetchNodeParsesXmlResponse() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(new ByteArrayInputStream(SAMPLE_XML.getBytes(StandardCharsets.UTF_8)));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        OsmNode osmNode = dataService.fetchNode(5589879349L);

        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(requestCaptor.getValue().uri().toString())
                .isEqualTo("https://www.openstreetmap.org/api/0.6/node/5589879349");

        assertThat(osmNode.nodeId()).isEqualTo(5589879349L);
        assertThat(osmNode.name()).isEqualTo("Rada Coffee & Rösterei");
        assertThat(osmNode.amenity()).isEqualTo("cafe");
        assertThat(osmNode.street()).isEqualTo("Untere Straße");
        assertThat(osmNode.houseNumber()).isEqualTo("21");
        assertThat(osmNode.postalCode()).isEqualTo("69117");
        assertThat(osmNode.city()).isEqualTo("Heidelberg");
        assertThat(osmNode.latitude()).isEqualTo(49.4122362);
        assertThat(osmNode.longitude()).isEqualTo(8.7077883);
        assertThat(osmNode.openingHours()).isEqualTo("Mo-Fr 11:00-18:00");
        assertThat(osmNode.phone()).isEqualTo("+49 6221 1805585");
        assertThat(osmNode.website()).isEqualTo("https://rada-roesterei.com/");
  assertThat(osmNode.shop()).isNull();
    }

    @Test
    void fetchNodeTranslatesNotFound() throws Exception {
        when(httpResponse.statusCode()).thenReturn(404);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        assertThatThrownBy(() -> dataService.fetchNode(999L))
                .isInstanceOf(OsmNodeNotFoundException.class);
    }

    @Test
    void fetchNodeRejectsNonPositiveIds() {
        assertThatThrownBy(() -> dataService.fetchNode(0L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
