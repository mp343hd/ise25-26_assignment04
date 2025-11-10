package de.seuhd.campuscoffee.domain.impl;

import de.seuhd.campuscoffee.domain.exceptions.OsmNodeMissingFieldsException;
import de.seuhd.campuscoffee.domain.model.CampusType;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.model.Pos;
import de.seuhd.campuscoffee.domain.model.PosType;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import de.seuhd.campuscoffee.domain.ports.PosDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosServiceImplTest {

    @Mock
    private PosDataService posDataService;

    @Mock
    private OsmDataService osmDataService;

    @Captor
    private ArgumentCaptor<Pos> posCaptor;

    private PosServiceImpl posService;

    @BeforeEach
    void setUp() {
	posService = new PosServiceImpl(posDataService, osmDataService);
    }

    @Test
    void importFromOsmNodeMapsFieldsCorrectly() {
	Long nodeId = 5589879349L;
	OsmNode osmNode = OsmNode.builder()
		.nodeId(nodeId)
		.name("Rada Coffee & Rösterei")
		.amenity("cafe")
		.description("Caffé und Rösterei")
		.latitude(49.4122362)
		.longitude(8.7077883)
		.street("Untere Straße")
		.houseNumber("21")
		.postalCode("69117")
		.city("Heidelberg")
		.openingHours("Mo-Fr 11:00-18:00")
		.phone("+49 6221 1805585")
		.website("https://rada-roesterei.com/")
		.build();
	when(osmDataService.fetchNode(nodeId)).thenReturn(osmNode);

	Pos persisted = Pos.builder()
		.id(42L)
		.name("Rada Coffee & Rösterei")
		.description("Caffé und Rösterei")
		.type(PosType.CAFE)
		.campus(CampusType.ALTSTADT)
		.street("Untere Straße")
		.houseNumber("21")
		.postalCode(69117)
		.city("Heidelberg")
		.build();

	when(posDataService.upsert(any(Pos.class))).thenReturn(persisted);

	Pos result = posService.importFromOsmNode(nodeId);

	verify(posDataService).upsert(posCaptor.capture());
	Pos mapped = posCaptor.getValue();
	assertThat(mapped.id()).isNull();
	assertThat(mapped.name()).isEqualTo("Rada Coffee & Rösterei");
	assertThat(mapped.description()).isEqualTo("Caffé und Rösterei");
	assertThat(mapped.street()).isEqualTo("Untere Straße");
	assertThat(mapped.houseNumber()).isEqualTo("21");
	assertThat(mapped.postalCode()).isEqualTo(69117);
	assertThat(mapped.city()).isEqualTo("Heidelberg");

	assertThat(result).isEqualTo(persisted);
    }

    @Test
    void importFromOsmNodeThrowsWhenRequiredFieldsMissing() {
	Long nodeId = 123L;
	OsmNode incompleteNode = OsmNode.builder()
		.nodeId(nodeId)
		.amenity("cafe")
		.name("Unnamed")
		.street("Teststraße")
		.postalCode("69117")
		.city("Heidelberg")
		.build();
	when(osmDataService.fetchNode(nodeId)).thenReturn(incompleteNode);

	assertThatThrownBy(() -> posService.importFromOsmNode(nodeId))
		.isInstanceOf(OsmNodeMissingFieldsException.class)
		.hasMessageContaining("addr:housenumber");

	verify(posDataService, never()).upsert(any(Pos.class));
    }

    @Test
    void importFromOsmNodeResolvesPosTypeFromAmenityAndShop() {
	Long nodeId = 456L;
	OsmNode osmNode = OsmNode.builder()
		.nodeId(nodeId)
		.amenity("canteen")
		.name("Mensa")
		.street("Main Street")
		.houseNumber("1")
		.postalCode("69120")
		.city("Heidelberg")
		.build();
	when(osmDataService.fetchNode(nodeId)).thenReturn(osmNode);

	Pos persisted = Pos.builder()
		.id(99L)
		.name("Mensa")
		.description("Imported from OSM node " + nodeId)
		.type(PosType.CAFETERIA)
		.campus(CampusType.INF)
		.street("Main Street")
		.houseNumber("1")
		.postalCode(69120)
		.city("Heidelberg")
		.build();
	when(posDataService.upsert(any(Pos.class))).thenReturn(persisted);

	Pos result = posService.importFromOsmNode(nodeId);

	verify(posDataService).upsert(posCaptor.capture());
	Pos mapped = posCaptor.getValue();
	assertThat(mapped.type()).isEqualTo(PosType.CAFETERIA);
	assertThat(mapped.campus()).isEqualTo(CampusType.INF);

	assertThat(result).isEqualTo(persisted);
    }

    @Test
    void importFromOsmNodeDefaultsToBakeryForShop() {
	Long nodeId = 777L;
	OsmNode osmNode = OsmNode.builder()
		.nodeId(nodeId)
		.shop("bakery")
		.name("Bakery Bliss")
		.street("Bread Lane")
		.houseNumber("5")
		.postalCode("69115")
		.city("Heidelberg")
		.build();
	when(osmDataService.fetchNode(nodeId)).thenReturn(osmNode);

	Pos persisted = Pos.builder()
		.id(100L)
		.name("Bakery Bliss")
		.description("Imported from OSM node " + nodeId)
		.type(PosType.BAKERY)
		.campus(CampusType.BERGHEIM)
		.street("Bread Lane")
		.houseNumber("5")
		.postalCode(69115)
		.city("Heidelberg")
		.build();
	when(posDataService.upsert(any(Pos.class))).thenReturn(persisted);

	Pos result = posService.importFromOsmNode(nodeId);

	verify(posDataService).upsert(posCaptor.capture());
	Pos mapped = posCaptor.getValue();
	assertThat(mapped.type()).isEqualTo(PosType.BAKERY);
	assertThat(mapped.campus()).isEqualTo(CampusType.BERGHEIM);

	assertThat(result).isEqualTo(persisted);
    }
}
