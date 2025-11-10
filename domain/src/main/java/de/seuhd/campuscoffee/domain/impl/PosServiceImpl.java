package de.seuhd.campuscoffee.domain.impl;

import de.seuhd.campuscoffee.domain.exceptions.DuplicatePosNameException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeMissingFieldsException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.CampusType;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.model.Pos;
import de.seuhd.campuscoffee.domain.exceptions.PosNotFoundException;
import de.seuhd.campuscoffee.domain.model.PosType;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import de.seuhd.campuscoffee.domain.ports.PosDataService;
import de.seuhd.campuscoffee.domain.ports.PosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Implementation of the POS service that handles business logic related to POS entities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PosServiceImpl implements PosService {
    private final PosDataService posDataService;
    private final OsmDataService osmDataService;

    @Override
    public void clear() {
        log.warn("Clearing all POS data");
        posDataService.clear();
    }

    @Override
    public @NonNull List<Pos> getAll() {
        log.debug("Retrieving all POS");
        return posDataService.getAll();
    }

    @Override
    public @NonNull Pos getById(@NonNull Long id) throws PosNotFoundException {
        log.debug("Retrieving POS with ID: {}", id);
        return posDataService.getById(id);
    }

    @Override
    public @NonNull Pos upsert(@NonNull Pos pos) throws PosNotFoundException {
        if (pos.id() == null) {
            // Create new POS
            log.info("Creating new POS: {}", pos.name());
            return performUpsert(pos);
        } else {
            // Update existing POS
            log.info("Updating POS with ID: {}", pos.id());
            // POS ID must be set
            Objects.requireNonNull(pos.id());
            // POS must exist in the database before the update
            posDataService.getById(pos.id());
            return performUpsert(pos);
        }
    }

    @Override
    public @NonNull Pos importFromOsmNode(@NonNull Long nodeId) throws OsmNodeNotFoundException {
        log.info("Importing POS from OpenStreetMap node {}...", nodeId);

        // Fetch the OSM node data using the port
        OsmNode osmNode = osmDataService.fetchNode(nodeId);

    // Convert OSM node to POS domain object and upsert it
        Pos savedPos = upsert(convertOsmNodeToPos(osmNode));
        log.info("Successfully imported POS '{}' from OSM node {}", savedPos.name(), nodeId);

        return savedPos;
    }

    /**
     * Converts an OSM node to a POS domain object.
     */
    private @NonNull Pos convertOsmNodeToPos(@NonNull OsmNode osmNode) {
    String amenity = osmNode.amenity();
    String shop = osmNode.shop();

        String name = required(osmNode.name(), "name", osmNode.nodeId());
        String street = required(osmNode.street(), "addr:street", osmNode.nodeId());
        String houseNumber = required(osmNode.houseNumber(), "addr:housenumber", osmNode.nodeId());
        String city = required(osmNode.city(), "addr:city", osmNode.nodeId());
        String postalCodeRaw = required(osmNode.postalCode(), "addr:postcode", osmNode.nodeId());

        Integer postalCode;
        try {
            postalCode = Integer.valueOf(postalCodeRaw);
        } catch (NumberFormatException e) {
            throw new OsmNodeMissingFieldsException(osmNode.nodeId(), "postal code must be numeric");
        }

        String description = normalize(osmNode.description());
        if (description == null) {
            description = "Imported from OSM node " + osmNode.nodeId();
            if (osmNode.latitude() != null && osmNode.longitude() != null) {
                description += String.format(" (lat=%s, lon=%s)", osmNode.latitude(), osmNode.longitude());
            }
        }

    PosType posType = resolvePosType(amenity, shop);
        CampusType campus = resolveCampus(postalCode);

        return Pos.builder()
                .name(name)
                .description(description)
                .type(posType)
                .campus(campus)
                .street(street)
                .houseNumber(houseNumber)
                .postalCode(postalCode)
                .city(city)
                .build();
    }

    private static PosType resolvePosType(String amenity, String shop) {
        String normalizedAmenity = normalize(amenity);
        if (normalizedAmenity != null) {
            if ("canteen".equalsIgnoreCase(normalizedAmenity)) {
                return PosType.CAFETERIA;
            }
            if ("vending_machine".equalsIgnoreCase(normalizedAmenity)) {
                return PosType.VENDING_MACHINE;
            }
            if ("cafe".equalsIgnoreCase(normalizedAmenity)) {
                return PosType.CAFE;
            }
        }

        String normalizedShop = normalize(shop);
        if (normalizedShop != null && "bakery".equalsIgnoreCase(normalizedShop)) {
            return PosType.BAKERY;
        }
        return PosType.CAFE;
    }

    private static CampusType resolveCampus(Integer postalCode) {
        if (postalCode == null) {
            return CampusType.ALTSTADT;
        }
        return switch (postalCode) {
            case 69117 -> CampusType.ALTSTADT;
            case 69115 -> CampusType.BERGHEIM;
            case 69120 -> CampusType.INF;
            default -> CampusType.ALTSTADT;
        };
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String required(String value, String fieldName, Long nodeId) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new OsmNodeMissingFieldsException(nodeId, "missing " + fieldName);
        }
        return normalized;
    }

    /**
     * Performs the actual upsert operation with consistent error handling and logging.
     * Database constraint enforces name uniqueness - data layer will throw DuplicatePosNameException if violated.
     * JPA lifecycle callbacks (@PrePersist/@PreUpdate) set timestamps automatically.
     *
     * @param pos the POS to upsert
     * @return the persisted POS with updated ID and timestamps
     * @throws DuplicatePosNameException if a POS with the same name already exists
     */
    private @NonNull Pos performUpsert(@NonNull Pos pos) throws DuplicatePosNameException {
        try {
            Pos upsertedPos = posDataService.upsert(pos);
            log.info("Successfully upserted POS with ID: {}", upsertedPos.id());
            return upsertedPos;
        } catch (DuplicatePosNameException e) {
            log.error("Error upserting POS '{}': {}", pos.name(), e.getMessage());
            throw e;
        }
    }
}
