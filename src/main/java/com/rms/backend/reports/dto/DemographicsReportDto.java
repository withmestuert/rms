package com.rms.backend.reports.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemographicsReportDto {
    private List<ProfessionStatDto> professions;
    private List<HometownStatDto> hometowns;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProfessionStatDto {
        private String name;
        private int count;
        private int percent;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HometownStatDto {
        private String city;
        private int count;
        private int percent;
        private String color;
    }
}
