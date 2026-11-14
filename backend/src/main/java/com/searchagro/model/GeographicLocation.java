package com.searchagro.model;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class GeographicLocation {

    @JsonProperty("place_id")
    private long placeId;
    private String licence;
    @JsonProperty("osm_type")
    private String osmType;
    @JsonProperty("osm_id")
    private long osmId;
    private String lat;
    private String lon;
    private String category;
    private String type;
    @JsonProperty("place_rank")
    private Integer placeRank;
    private Double importance;
    @JsonProperty("addresstype")
    private String addressType;
    private String name;
    @JsonProperty("display_name")
    private String displayName;
    private JsonNode address;
    @JsonProperty("boundingbox")
    private JsonNode boundingBox;
    private JsonNode extratags;
    private JsonNode namedetails;
    @JsonIgnore
    private JsonNode originalJson;
    @JsonIgnore
    private final Map<String, JsonNode> additionalFields = new LinkedHashMap<>();

    public GeographicLocation() {
    }

    @JsonAnySetter
    public void addAdditionalField(String name, JsonNode value) {
        additionalFields.put(name, value);
    }

    @JsonAnyGetter
    public Map<String, JsonNode> getAdditionalFields() {
        return additionalFields;
    }

    @JsonIgnore
    public JsonNode getOriginalJson() {
        return originalJson;
    }

    @JsonIgnore
    public void setOriginalJson(JsonNode originalJson) {
        this.originalJson = originalJson;
    }

    public long getPlaceId() {
        return placeId;
    }

    public void setPlaceId(long placeId) {
        this.placeId = placeId;
    }

    public String getLicence() {
        return licence;
    }

    public void setLicence(String licence) {
        this.licence = licence;
    }

    public String getOsmType() {
        return osmType;
    }

    public void setOsmType(String osmType) {
        this.osmType = osmType;
    }

    public long getOsmId() {
        return osmId;
    }

    public void setOsmId(long osmId) {
        this.osmId = osmId;
    }

    public String getLat() {
        return lat;
    }

    public void setLat(String lat) {
        this.lat = lat;
    }

    public String getLon() {
        return lon;
    }

    public void setLon(String lon) {
        this.lon = lon;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getPlaceRank() {
        return placeRank;
    }

    public void setPlaceRank(Integer placeRank) {
        this.placeRank = placeRank;
    }

    public Double getImportance() {
        return importance;
    }

    public void setImportance(Double importance) {
        this.importance = importance;
    }

    public String getAddressType() {
        return addressType;
    }

    public void setAddressType(String addressType) {
        this.addressType = addressType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public JsonNode getAddress() {
        return address;
    }

    public void setAddress(JsonNode address) {
        this.address = address;
    }

    public JsonNode getBoundingBox() {
        return boundingBox;
    }

    public void setBoundingBox(JsonNode boundingBox) {
        this.boundingBox = boundingBox;
    }

    public JsonNode getExtratags() {
        return extratags;
    }

    public void setExtratags(JsonNode extratags) {
        this.extratags = extratags;
    }

    public JsonNode getNamedetails() {
        return namedetails;
    }

    public void setNamedetails(JsonNode namedetails) {
        this.namedetails = namedetails;
    }
}
