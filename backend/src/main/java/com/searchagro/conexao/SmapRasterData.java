package com.searchagro.conexao;

public class SmapRasterData {

    private final byte[] morning;
    private final byte[] afternoon;

    public SmapRasterData(byte[] morning, byte[] afternoon) {
        this.morning = morning;
        this.afternoon = afternoon;
    }

    public byte[] getMorning() {
        return morning;
    }

    public byte[] getAfternoon() {
        return afternoon;
    }
}
