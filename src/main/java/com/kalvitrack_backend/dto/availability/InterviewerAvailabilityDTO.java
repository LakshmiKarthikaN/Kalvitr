package com.kalvitrack_backend.dto.availability;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;
@Getter
@Setter
@NoArgsConstructor
public class InterviewerAvailabilityDTO {

    @JsonProperty("selectedDates")
    @NotNull(message = "Selected dates are required")
    private List<String> selectedDates;
    @JsonProperty("slotDurationMinutes")
    private Integer slotDurationMinutes;
    @JsonProperty("timeSlots")
    @NotNull(message = "Time slots are required")
    private Map<String, List<TimeSlot>> timeSlots;

    // Constructors

    public InterviewerAvailabilityDTO(List<String> selectedDates, Map<String, List<TimeSlot>> timeSlots) {
        this.selectedDates = selectedDates;
        this.timeSlots = timeSlots;
    }


    // Inner class for TimeSlot
    @Getter
    @Setter
    public static class TimeSlot {
        @JsonProperty("startTime")
        @NotNull(message = "Start time is required")
        @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Start time must be in HH:MM format")
        private String startTime;

        @JsonProperty("endTime")
        @NotNull(message = "End time is required")
        @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "End time must be in HH:MM format")
        private String endTime;

        @JsonProperty("notes")
        private String notes;

        // Constructors
        public TimeSlot() {}

        public TimeSlot(String startTime, String endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public TimeSlot(String startTime, String endTime, String notes) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.notes = notes;
        }


        @Override
        public String toString() {
            return "TimeSlot{" +
                    "startTime='" + startTime + '\'' +
                    ", endTime='" + endTime + '\'' +
                    ", notes='" + notes + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "InterviewerAvailabilityDTO{" +
                "selectedDates=" + selectedDates +
                ", timeSlots=" + timeSlots +
                '}';
    }
}