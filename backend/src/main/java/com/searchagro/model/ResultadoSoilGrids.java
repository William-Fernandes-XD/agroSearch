package com.searchagro.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

public class ResultadoSoilGrids {

    private String type;
    private Geometry geometry;
    private SoilProperties properties;

    public ResultadoSoilGrids() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public SoilProperties getProperties() {
        return properties;
    }

    public void setProperties(SoilProperties properties) {
        this.properties = properties;
    }

    public Layer getLayer(String propertyName) {
        if (properties == null || properties.getLayers() == null) {
            return null;
        }
        for (Layer layer : properties.getLayers()) {
            if (layer != null && propertyName.equalsIgnoreCase(layer.getName())) {
                return layer;
            }
        }
        return null;
    }

    public Integer getDFactor(String propertyName) {
        Layer layer = getLayer(propertyName);
        if (layer != null && layer.getUnitMeasure() != null) {
            return layer.getUnitMeasure().getDFactor();
        }
        return null;
    }

    public Double getMean(String propertyName, String depthLabel) {
        Layer layer = getLayer(propertyName);
        if (layer == null || layer.getDepths() == null) {
            return null;
        }
        for (Depth depth : layer.getDepths()) {
            if (depth != null && depthLabel.equalsIgnoreCase(depth.getLabel())) {
                if (depth.getValues() != null) {
                    return depth.getValues().getMean();
                }
            }
        }
        return null;
    }

    /**
     * Calcula o valor de pH respeitando o fator de escala d_factor retornado pelos metadados da API.
     * Se o valor de mean for nulo, retorna null. Nunca substitui nulo por zero.
     */
    public Double calcularPh(String propertyName, String depthLabel) {
        Double mean = getMean(propertyName, depthLabel);
        if (mean == null) {
            return null;
        }

        Integer dFactor = getDFactor(propertyName);
        double divisor = (dFactor != null && dFactor > 0) ? dFactor.doubleValue() : 10.0;
        double valorConvertido = mean / divisor;

        return BigDecimal.valueOf(valorConvertido)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static class Geometry {
        private String type;
        private List<Double> coordinates;

        public Geometry() {
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<Double> getCoordinates() {
            return coordinates;
        }

        public void setCoordinates(List<Double> coordinates) {
            this.coordinates = coordinates;
        }
    }

    public static class SoilProperties {
        private List<Layer> layers;

        public SoilProperties() {
        }

        public List<Layer> getLayers() {
            return layers;
        }

        public void setLayers(List<Layer> layers) {
            this.layers = layers;
        }
    }

    public static class Layer {
        private String name;

        @SerializedName("unit_measure")
        @JsonProperty("unit_measure")
        private UnitMeasure unitMeasure;

        private List<Depth> depths;

        public Layer() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public UnitMeasure getUnitMeasure() {
            return unitMeasure;
        }

        public void setUnitMeasure(UnitMeasure unitMeasure) {
            this.unitMeasure = unitMeasure;
        }

        public List<Depth> getDepths() {
            return depths;
        }

        public void setDepths(List<Depth> depths) {
            this.depths = depths;
        }
    }

    public static class UnitMeasure {
        @SerializedName("d_factor")
        @JsonProperty("d_factor")
        private Integer dFactor;

        @SerializedName("mapped_units")
        @JsonProperty("mapped_units")
        private String mappedUnits;

        @SerializedName("target_units")
        @JsonProperty("target_units")
        private String targetUnits;

        public UnitMeasure() {
        }

        public Integer getDFactor() {
            return dFactor;
        }

        public void setDFactor(Integer dFactor) {
            this.dFactor = dFactor;
        }

        public String getMappedUnits() {
            return mappedUnits;
        }

        public void setMappedUnits(String mappedUnits) {
            this.mappedUnits = mappedUnits;
        }

        public String getTargetUnits() {
            return targetUnits;
        }

        public void setTargetUnits(String targetUnits) {
            this.targetUnits = targetUnits;
        }
    }

    public static class Depth {
        private String label;
        private Values values;

        public Depth() {
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public Values getValues() {
            return values;
        }

        public void setValues(Values values) {
            this.values = values;
        }
    }

    public static class Values {
        private Double mean;

        public Values() {
        }

        public Double getMean() {
            return mean;
        }

        public void setMean(Double mean) {
            this.mean = mean;
        }
    }
}
