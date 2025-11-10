# Project Requirement Proposal (PRP)
<!-- Adapted from https://github.com/Wirasm/PRPs-agentic-eng/tree/development/PRPs -->

You are a senior software engineer.
Use the information below to implement a new feature or improvement in this software project.

## Goal

**Feature Goal**: Implement a feature that imports a new Point of Sale (POS) into the CampusCoffee system based on an existing OpenStreetMap (OSM) entry.

**Deliverable**: A new REST API endpoint (e.g. POST /api/pos/import/osm/{osmNodeId}) and supporting service classes that retrieve, parse, and persist OSM-based café data into the CampusCoffee database.

**Success Definition**: When a valid OSM node ID (e.g. 5589879349 for “Rada Coffee & Rösterei”) is provided, the system fetches the corresponding XML data from the OSM API, maps it to a new POS entity, saves it, and returns the created POS object as JSON.

## User Persona

**Target User**: Administrator or developer responsible for maintaining café locations in the CampusCoffee platform.

**Use Case**: The user wants to add a real café to CampusCoffee by importing it directly from OpenStreetMap instead of creating it manually.

**User Journey**:
1. The user calls the new import endpoint with a specific OSM node ID.
2. The system retrieves the node’s XML data from https://www.openstreetmap.org/api/0.6/node/{id}.
3. Relevant information such as coordinates and tags is extracted.
4. A new PointOfSale entity is created and persisted in the database.
5. The system returns a JSON response confirming the successful import.

**Pain Points Addressed**: [Specific user frustrations this feature solves]

## Why

- Avoids repetitive manual data entry.
- Reduces errors and inconsistencies in café information.
- Simplifies the process of adding accurate, georeferenced POS data.

## What

The feature adds an OSM import function with these behaviors and requirements:
- Input: OSM node ID (integer).
- Process:
  - Fetch XML via GET https://www.openstreetmap.org/api/0.6/node/{id}.
  - Parse attributes:
    - <node lat="..." lon="..."> → coordinates.
    - <tag k="name" v="...">, <tag k="addr:street" v="...">, <tag k="addr:city" v="...">, <tag k="opening_hours" v="...">, etc.
  - Map these to a PointOfSale domain object.
  - Persist the new POS via the repository adapter.
- Output: JSON object representing the created POS.

Technical Notes:
- Create a new OsmClient in the application module to handle HTTP requests and XML parsing.
- Implement a PosImportService that orchestrates validation, data fetching, and persistence.
- Add a controller class PosImportController in the api module exposing the endpoint.
- Add configuration entry osm.base-url=https://www.openstreetmap.org.
- Include meaningful exception handling (invalid ID, network error, or non-café entries).
- Write tests for XML parsing, mapping, and API behavior.

### Success Criteria
- Valid OSM node ID creates and stores a new POS entity.
- Returned JSON contains correct name, address, coordinates, and optional fields.
- Invalid or missing node IDs return descriptive 4xx errors.
- Feature passes integration tests and builds successfully.
- Code respects the ports-and-adapters structure.

## Documentation & References

MUST READ - Include the following information in your context window.

The `README.md` file at the root of the project contains setup instructions and example API calls.

This Java Spring Boot application is structured as a multi-module Maven project following the ports-and-adapters architectural pattern.
There are the following submodules:

`api` - Maven submodule for controller adapter.

`application` - Maven submodule for Spring Boot application, test data import, and system tests.

`data` - Maven submodule for data adapter.

`domain` - Maven submodule for domain model, main business logic, and ports.

Additional References:
- OpenStreetMap XML format: https://wiki.openstreetmap.org/wiki/OSM_XML
- Example node (https://www.openstreetmap.org/api/0.6/node/5589879349):
<osm version="0.6" generator="openstreetmap-cgimap 2.1.0 (113209 spike-06.openstreetmap.org)" copyright="OpenStreetMap and contributors" attribution="http://www.openstreetmap.org/copyright" license="http://opendatacommons.org/licenses/odbl/1-0/">
<node id="5589879349" visible="true" version="11" changeset="165984125" timestamp="2025-05-08T13:35:07Z" user="Niiepce" uid="12992079" lat="49.4122362" lon="8.7077883">
<tag k="addr:city" v="Heidelberg"/>
<tag k="addr:country" v="DE"/>
<tag k="addr:housenumber" v="21"/>
<tag k="addr:postcode" v="69117"/>
<tag k="addr:street" v="Untere Straße"/>
<tag k="amenity" v="cafe"/>
<tag k="cuisine" v="coffee_shop;breakfast"/>
<tag k="description" v="Caffé und Rösterei"/>
<tag k="diet:vegan" v="yes"/>
<tag k="diet:vegetarian" v="yes"/>
<tag k="indoor_seating" v="yes"/>
<tag k="internet_access:fee" v="no"/>
<tag k="name" v="Rada"/>
<tag k="name:de" v="Rada Coffee & Rösterei"/>
<tag k="name:en" v="Rada Coffee & Rösterei"/>
<tag k="opening_hours" v="Mo-Fr 11:00-18:00; Sa-Su 10:00-18:00"/>
<tag k="outdoor_seating" v="yes"/>
<tag k="phone" v="+49 6221 1805585"/>
<tag k="smoking" v="outside"/>
<tag k="website" v="https://rada-roesterei.com/"/>
</node>
</osm>