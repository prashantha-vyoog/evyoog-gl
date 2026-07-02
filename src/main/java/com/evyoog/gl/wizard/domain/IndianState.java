package com.evyoog.gl.wizard.domain;

public enum IndianState {
    ANDHRA_PRADESH("Andhra Pradesh", "37"),
    ARUNACHAL_PRADESH("Arunachal Pradesh", "12"),
    ASSAM("Assam", "18"),
    BIHAR("Bihar", "10"),
    CHHATTISGARH("Chhattisgarh", "22"),
    GOA("Goa", "30"),
    GUJARAT("Gujarat", "24"),
    HARYANA("Haryana", "06"),
    HIMACHAL_PRADESH("Himachal Pradesh", "02"),
    JHARKHAND("Jharkhand", "20"),
    KARNATAKA("Karnataka", "29"),
    KERALA("Kerala", "32"),
    MADHYA_PRADESH("Madhya Pradesh", "23"),
    MAHARASHTRA("Maharashtra", "27"),
    MANIPUR("Manipur", "14"),
    MEGHALAYA("Meghalaya", "17"),
    MIZORAM("Mizoram", "15"),
    NAGALAND("Nagaland", "13"),
    ODISHA("Odisha", "21"),
    PUNJAB("Punjab", "03"),
    RAJASTHAN("Rajasthan", "08"),
    SIKKIM("Sikkim", "11"),
    TAMIL_NADU("Tamil Nadu", "33"),
    TELANGANA("Telangana", "36"),
    TRIPURA("Tripura", "16"),
    UTTAR_PRADESH("Uttar Pradesh", "09"),
    UTTARAKHAND("Uttarakhand", "05"),
    WEST_BENGAL("West Bengal", "19"),
    DELHI("Delhi (NCT)", "07"),
    JAMMU_KASHMIR("Jammu & Kashmir", "01"),
    LADAKH("Ladakh", "38");

    private final String stateName;
    private final String stateCode;

    IndianState(String stateName, String stateCode) {
        this.stateName = stateName;
        this.stateCode = stateCode;
    }

    public String getStateName() {
        return stateName;
    }

    public String getStateCode() {
        return stateCode;
    }
}
